package com.lolinhouse.record;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.Side;
import com.lolinhouse.domain.stats.Kda;
import com.lolinhouse.domain.stats.PlayTotals;
import com.lolinhouse.domain.stats.RankingKey;
import com.lolinhouse.domain.stats.StatSummary;
import com.lolinhouse.session.FearlessMode;
import com.lolinhouse.stats.ChampionStatsResponse;
import com.lolinhouse.stats.HomeResponse;
import com.lolinhouse.stats.PlayerListResponse;
import com.lolinhouse.stats.PlayerRecordResponse;
import com.lolinhouse.stats.RankingResponse;
import com.lolinhouse.stats.SummaryView;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 역할: 공개 응답 DTO의 직렬화 계약(필드 이름, 날짜는 문자열, 금지 키 없음)을 DB 없이 고정한다.
 * 중첩 SetView(프로필 recentSets, 홈 recentSets, 기록 페이지)까지 금지 키 검사를 통과해야 한다.
 */
class PublicDtoContractTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static SetView sampleSet() {
        List<SetView.PlayerLine> lines = List.of(
                new SetView.PlayerLine(1, "홍길동", true, Position.TOP, "Ahri", 1, 2, 3, 4, 5));
        SetView.Displayed displayed = new SetView.Displayed(10, 5, 20, 60000);
        return new SetView(11, 1, 21, 1, 31, LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 12), 1800,
                Side.BLUE, DuelTeam.TEAM1,
                List.of(new SetView.TeamView(DuelTeam.TEAM1, Side.BLUE, true, displayed, lines),
                        new SetView.TeamView(DuelTeam.TEAM2, Side.RED, false, displayed, lines)),
                List.of(new SetView.BanView(1, Side.BLUE, "Lux")), List.of("Zed"));
    }

    private static SummaryView sampleSummary() {
        return SummaryView.from(new StatSummary(2, 1, new Kda(3, 2, 5)), new PlayTotals(300, 20000));
    }

    @Test
    void 세트_뷰는_약속한_키와_문자열_날짜로_직렬화된다() {
        JsonNode json = MAPPER.valueToTree(sampleSet());

        assertThat(json.propertyNames()).containsExactlyInAnyOrder("setId", "setNo", "duelId", "duelSeq", "sessionId",
                "sessionDate", "shotDate", "durationSec", "team1Side", "winnerTeam", "teams", "bans",
                "fearlessRestricted");
        assertThat(json.get("sessionDate").asString()).isEqualTo("2026-10-12");
        assertThat(json.get("teams").get(0).propertyNames()).containsExactlyInAnyOrder("team", "side", "won",
                "displayed", "players");
        assertThat(json.get("teams").get(0).get("players").get(0).propertyNames()).containsExactlyInAnyOrder(
                "playerId", "displayName", "registered", "position", "championKey", "kills", "deaths", "assists",
                "cs", "gold");
    }

    @Test
    void 모든_공개_응답_DTO가_금지_키를_담지_않는다_중첩_recentSets_포함() {
        Map<DuelTeam, Integer> score = new EnumMap<>(DuelTeam.class);
        score.put(DuelTeam.TEAM1, 2);
        score.put(DuelTeam.TEAM2, 1);
        Map<Position, HomeResponse.TopPick> picks = new EnumMap<>(Position.class);
        picks.put(Position.TOP, new HomeResponse.TopPick("Ahri", 3, 2));
        picks.put(Position.MID, null);
        Map<Position, PlayerRecordResponse.LaneTierView> lanes = new EnumMap<>(Position.class);
        lanes.put(Position.TOP, new PlayerRecordResponse.LaneTierView(2, com.lolinhouse.domain.tier.TierSub.HIGH, null));

        List<Object> dtos = List.of(
                sampleSet(),
                new RecordPageResponse(List.of(new RecordPageResponse.SessionBlock(31, LocalDate.of(2026, 10, 12),
                        List.of(new RecordPageResponse.DuelBlock(21, 1, FearlessMode.RESET, score, 3,
                                List.of(sampleSet()))))), LocalDate.of(2026, 10, 1),
                        new RecordPageResponse.Totals(1, 3)),
                new DuelDetailResponse(21, 1, FearlessMode.RESET, score, 3, 31, LocalDate.of(2026, 10, 12),
                        List.of(sampleSet()),
                        List.of(new DuelDetailResponse.Personal(1, "홍길동", true, List.of(DuelTeam.TEAM1), 2, 1,
                                com.lolinhouse.domain.stats.DuelResult.WIN)),
                        new DuelDetailResponse.Fearless(List.of("Zed"), List.of("Lux"))),
                new PlayerListResponse(5, List.of(new PlayerListResponse.Item(1, "홍길동", true, Position.MID,
                        sampleSummary(), Map.of(Position.MID, 2),
                        List.of(new PlayerListResponse.TopChampion("Ahri", 2)), 1))),
                new PlayerRecordResponse(1, "홍길동", true, Position.MID, lanes, 5, 0.4, sampleSummary(),
                        List.of(new PlayerRecordResponse.PositionSummary(Position.MID, sampleSummary(), List.of())),
                        List.of(new PlayerRecordResponse.ChampionSummary("Ahri", sampleSummary())),
                        Map.of(Position.MID, List.of(new PlayerRecordResponse.ChampionSummary("Ahri", sampleSummary()))),
                        new PlayerRecordResponse.DuelResults(1, 0, 0, List.of(new PlayerRecordResponse.Item(21,
                                LocalDate.of(2026, 10, 12), 1, 2, 1, com.lolinhouse.domain.stats.DuelResult.WIN))),
                        List.of(sampleSet())),
                new RankingResponse("ALL", RankingKey.WIN_RATE, 5, 5,
                        List.of(new RankingResponse.Entry(1, "홍길동", true, null, sampleSummary()))),
                new ChampionStatsResponse(5, List.of(new ChampionStatsResponse.Item("Ahri", 3, 1, 2, 1, 4))),
                new HomeResponse(List.of(sampleSet()), picks));

        for (Object dto : dtos) {
            PublicJsonAssertions.assertNoForbiddenKeys(dto);
            PublicJsonAssertions.assertNoForbiddenKeysInJson(MAPPER.writeValueAsString(dto));
        }
    }

    @Test
    void 금지_키_검사는_중첩_배열_안의_키도_잡는다() {
        String leaked = "{\"recentSets\":[{\"teams\":[{\"players\":[{\"screenRow\":1}]}]}]}";

        assertThatThrownBy(() -> PublicJsonAssertions.assertNoForbiddenKeysInJson(leaked))
                .hasMessageContaining("$.recentSets[0].teams[0].players[0].screenRow");
        for (String key : PublicJsonAssertions.FORBIDDEN_KEYS) {
            assertThatThrownBy(() -> PublicJsonAssertions.assertNoForbiddenKeysInJson("{\"a\":{\"" + key + "\":null}}"))
                    .as(key).hasMessageContaining("$.a." + key);
        }
        assertThat(PublicJsonAssertions.FORBIDDEN_KEYS).contains("screenRow", "aliases", "updatedAt", "deletedAt",
                "realName", "imageKey", "imageHash", "fingerprint", "topTeam", "createdAt");
    }

    @Test
    void 요약의_KDA_비율은_세트가_없거나_PERFECT이면_null이다() {
        SummaryView empty = SummaryView.from(new StatSummary(0, 0, Kda.ZERO), new PlayTotals(0, 0));
        SummaryView perfect = SummaryView.from(new StatSummary(1, 1, new Kda(5, 0, 3)), new PlayTotals(1, 1));
        SummaryView normal = SummaryView.from(new StatSummary(2, 1, new Kda(3, 2, 5)), new PlayTotals(1, 1));

        assertThat(empty.kda().ratio()).isNull();
        assertThat(empty.sets()).isZero();
        assertThat(perfect.kda().ratio()).isNull();
        assertThat(normal.kda().ratio()).isEqualTo(4.0);
        assertThat(normal.losses()).isEqualTo(1);
        assertThat(normal.winRate()).isEqualTo(0.5);
    }

    @Test
    void 대결_점수_키는_TEAM1_TEAM2이고_홈의_빈_라인은_null로_나간다() {
        Map<DuelTeam, Integer> score = new EnumMap<>(DuelTeam.class);
        score.put(DuelTeam.TEAM1, 2);
        score.put(DuelTeam.TEAM2, 1);
        Map<Position, HomeResponse.TopPick> picks = new EnumMap<>(Position.class);
        picks.put(Position.TOP, null);

        JsonNode block = MAPPER.valueToTree(new RecordPageResponse.DuelBlock(1, 1, FearlessMode.CONTINUE, score, 3,
                List.of()));
        JsonNode home = MAPPER.valueToTree(new HomeResponse(List.of(), picks));

        assertThat(block.get("score").get("TEAM1").asInt()).isEqualTo(2);
        assertThat(block.get("fearlessMode").asString()).isEqualTo("CONTINUE");
        assertThat(home.get("topPicksByPosition").has("TOP")).isTrue();
        assertThat(home.get("topPicksByPosition").get("TOP").isNull()).isTrue();
    }
}
