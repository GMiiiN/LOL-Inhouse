package com.lolinhouse.ingest;

import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** 외부 호출 없이 순수 규칙을 검증한다. 관련 노트: docs/study/05-extraction-lab.md */
class PureLogicTest {
    @Test void boxUsesYFirstAndClips() {
        var r = Boxes.pixels(new int[]{100,200,900,800}, 200,100);
        assertEquals(new java.awt.Rectangle(40,10,120,80), r);
        assertEquals(new java.awt.Rectangle(0,0,200,100),
                Boxes.pixels(new int[]{-10,-20,1010,1020},200,100));
        assertThrows(IllegalArgumentException.class, () -> Boxes.pixels(new int[]{5,5,1,1},100,100));
        assertThrows(IllegalArgumentException.class, () -> Boxes.pixels(new int[]{1,2,3},100,100));
        assertThrows(IllegalArgumentException.class, () -> Boxes.pixels(new int[]{0,1100,10,1200},100,100));
    }
    @Test void tinyBoxHasAtLeastOnePixel() {
        assertEquals(1, Boxes.pixels(new int[]{1,1,2,2},10,10).width);
    }
    @Test void envParsesQuotesCommentsAndFirstEquals() {
        var values = Env.parse("\uFEFF" + """
# 설명
export A='a=b'
 B = "문자 # 유지"
C=value # 주석
D=
깨진줄
""");
        assertEquals(Map.of("A","a=b","B","문자 # 유지","C","value","D",""),values);
        assertEquals("환경", Env.choose("A",values,Map.of("A","환경")));
    }
    /** 가로·세로로 밝기가 변하는 테스트용 이미지. inverted면 밝기를 뒤집는다. */
    private static BufferedImage gradient(boolean inverted) {
        int size = IconMatcher.SIZE;
        var img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            int v = (x * 5 + y * 3) % 256;
            if (inverted) v = 255 - v;
            img.setRGB(x, y, (v << 16) | (v << 8) | v);
        }
        return img;
    }

    @Test void identicalImagesAreClosestAndBanSlashIsIgnored() {
        var a = gradient(false);
        var b = gradient(true);
        var plain = new IconMatcher.Settings(0, 0, false, false, 0.7);
        assertEquals(0, IconMatcher.distance(a, a, plain), 1e-9);
        assertTrue(IconMatcher.distance(a, a, plain) < IconMatcher.distance(a, b, plain));

        int size = IconMatcher.SIZE;
        var crossed = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++)
            crossed.setRGB(x, y, Math.abs(x + y - (size - 1)) < 3 ? 0xff0000 : a.getRGB(x, y));
        var slashIgnored = new IconMatcher.Settings(0, 0, false, true, 1.0);
        var slashCounted = new IconMatcher.Settings(0, 0, false, false, 1.0);
        assertTrue(IconMatcher.distance(a, crossed, slashIgnored) < IconMatcher.distance(a, crossed, slashCounted));
    }

    @Test void cropInsetRemovesBorderBeforeComparing() {
        // 공식 아이콘(icon)에 12% 두께의 흰 테두리를 두른 것이 결과창 밴 아이콘(framed)이라고 가정
        var icon = gradient(false);
        int size = icon.getWidth();
        int border = (int) Math.round(size * 0.12 / (1 - 2 * 0.12));
        var framed = new BufferedImage(size + 2 * border, size + 2 * border, BufferedImage.TYPE_INT_RGB);
        var g = framed.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, framed.getWidth(), framed.getHeight());
        g.drawImage(icon, border, border, null);
        g.dispose();
        var withInset = new IconMatcher.Settings(0.12, 0, false, false, 1.0);
        var withoutInset = new IconMatcher.Settings(0, 0, false, false, 1.0);
        var preparedIcon = IconMatcher.prepare(icon, 0);
        assertTrue(IconMatcher.distance(IconMatcher.prepare(framed, 0.12), preparedIcon, withInset)
                < IconMatcher.distance(IconMatcher.prepare(framed, 0), preparedIcon, withoutInset));
    }
    @Test void comparisonCountsMissingFieldsAndTruncatedPrefixes() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var truth=mapper.readTree("""
{"nick":"가나다라마","kda":[1,2,3],"cs":40,"gold":500}
""");
        var row=mapper.readTree("""
{"nickname":"가나다라…","kills":1,"deaths":2,"assists":4,"cs":40,"gold":500}
""");
        var c=new Comparison();
        c.row(truth,row);
        assertEquals(0,c.metrics().get("닉네임 정확").correct());
        assertEquals(1,c.metrics().get("닉네임 접두부 정규화").correct());
        assertEquals(0,c.metrics().get("어시스트").correct());
        assertEquals(1,c.metrics().get("CS").correct());
        assertFalse(Comparison.prefix("가…","가나다라마"));
        c.row(truth,mapper.nullNode());
        assertEquals(2,c.metrics().get("CS").total());
        assertEquals(1,c.metrics().get("CS").correct());
    }
    @Test void dateMustExistOnCalendar() {
        assertTrue(GeminiExtractor.isRealDate("2026/10/11"));
        assertTrue(GeminiExtractor.isRealDate("2028/02/29"));   // 윤년
        assertFalse(GeminiExtractor.isRealDate("2026/02/29"));  // 평년
        assertFalse(GeminiExtractor.isRealDate("2026/02/31"));
        assertFalse(GeminiExtractor.isRealDate("2026/99/99"));
        assertFalse(GeminiExtractor.isRealDate("2026-10-11"));
    }

    /** 검증을 통과하는 최소 응답. 테스트마다 일부를 망가뜨려 거부되는지 본다. */
    private static com.fasterxml.jackson.databind.node.ObjectNode validResponse() {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var root = mapper.createObjectNode().put("result", "승리").put("date", "2026/10/11").put("duration", "18:14");
        for (String side : java.util.List.of("top", "bottom")) {
            var team = root.putObject(side).put("kills", 1).put("deaths", 1).put("assists", 1).put("gold", 1);
            var rows = team.putArray("rows");
            var bans = team.putArray("banBoxes");
            for (int i = 0; i < 5; i++) {
                var row = rows.addObject().put("nickname", "n" + i).put("kills", 0).put("deaths", 0)
                        .put("assists", 0).put("cs", 0).put("gold", 0);
                row.putArray("portraitBox").add(10).add(10).add(20).add(20);
                bans.addArray().add(10).add(10).add(20).add(20);
            }
        }
        return root;
    }

    @Test void validationRejectsObjectsInsteadOfArraysAndHugeBoxValues() {
        assertDoesNotThrow(() -> GeminiExtractor.validate(validResponse()));

        // rows를 원소 5개짜리 객체로 바꿔치기: size()는 5지만 배열이 아니다
        var objectRows = validResponse();
        var rowsAsObject = objectRows.with("top").putObject("rows");
        for (int i = 0; i < 5; i++) rowsAsObject.putObject("r" + i);
        assertThrows(IllegalArgumentException.class, () -> GeminiExtractor.validate(objectRows));

        // int 범위를 넘는 좌표: asInt()만 쓰면 0으로 잘려 통과해 버린다
        var huge = validResponse();
        var box = (com.fasterxml.jackson.databind.node.ArrayNode) huge.with("top").withArray("banBoxes").get(0);
        box.set(0, com.fasterxml.jackson.databind.node.LongNode.valueOf(4294967296L));
        assertThrows(IllegalArgumentException.class, () -> GeminiExtractor.validate(huge));

        var badDate = validResponse().put("date", "2026/02/31");
        assertThrows(IllegalArgumentException.class, () -> GeminiExtractor.validate(badDate));
    }

    @Test void teamValidationFindsMismatches() throws Exception {
        var team=new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
{"kills":2,"deaths":0,"assists":0,"rows":[{"kills":1,"deaths":0,"assists":0}]}
""");
        assertTrue(Comparison.teamWarnings(team,"상단").stream().anyMatch(s->s.contains("kills")));
    }

    @Test void cropKeepsOriginalPixelsAtNormalizedBounds() {
        var image=new BufferedImage(20,10,BufferedImage.TYPE_INT_RGB);
        image.setRGB(4,1,0x123456);
        var crop=Boxes.crop(image,new int[]{100,200,900,800});
        assertEquals(12,crop.getWidth());
        assertEquals(8,crop.getHeight());
        assertEquals(0x123456,crop.getRGB(0,0)&0xffffff);
    }
    @Test void rankingReturnsKeyAndRunnerUp() {
        // 단색 이미지는 평균을 빼면 모두 같아지므로, 무늬가 다른 이미지 두 개로 시험한다
        var normal = gradient(false);
        var inverted = gradient(true);
        var matcher=new IconMatcher(java.util.List.of(
                new IconMatcher.Icon("1","SyntheticA",normal),
                new IconMatcher.Icon("2","SyntheticB",inverted)));
        var match=matcher.match(normal,false);
        assertEquals("1",match.best().championKey());
        assertEquals("2",match.runnerUp().championKey());
        assertTrue(match.margin()>0);
    }
}
