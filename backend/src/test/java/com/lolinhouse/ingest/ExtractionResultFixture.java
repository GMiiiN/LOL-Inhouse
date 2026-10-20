package com.lolinhouse.ingest;

import java.util.ArrayList;
import java.util.List;

import com.lolinhouse.champion.IconMatch;
import com.lolinhouse.ingest.extract.Attempt;
import com.lolinhouse.ingest.extract.ExtractedRow;
import com.lolinhouse.ingest.extract.ExtractedScoreboard;
import com.lolinhouse.ingest.extract.ExtractedTeam;
import com.lolinhouse.ingest.job.ExtractionResult;

/**
 * 역할: 테스트용 {@link ExtractionResult} 조립기. 작업자 C가 "작업 행을 ExtractionResult로 직접 INSERT"하는 픽스처로,
 * 작업자 B가 실행기 결과 형식을 단정하는 기준으로 쓴다. 챔피언 칸은 {@link SyntheticScoreboardImage}가 그려 넣은 챔피언을
 * 1순위(점수 0.9), 다음 index를 2·3순위(0.5, 0.4)로 둔다.
 */
public final class ExtractionResultFixture {

    private ExtractionResultFixture() {
    }

    /** 기본 합성 결과창({@link SyntheticScoreboardImage#defaultImage()}) 기준 결과. */
    public static ExtractionResult sample() {
        return from(SyntheticScoreboardImage.defaultImage());
    }

    public static ExtractionResult from(SyntheticScoreboardImage image) {
        ExtractedScoreboard scoreboard = image.scoreboard();
        return new ExtractionResult(ExtractionResult.CURRENT_SCHEMA_VERSION, "fake-model",
                FakeDataDragonSource.DEFAULT_VERSION, List.of(new Attempt(1, null, null, 1200, 0)),
                scoreboard.topResult(), scoreboard.shotDate().toString(), scoreboard.duration(),
                team(image, scoreboard.top(), true), team(image, scoreboard.bottom(), false));
    }

    private static ExtractionResult.Team team(SyntheticScoreboardImage image, ExtractedTeam team, boolean top) {
        List<ExtractionResult.Row> rows = new ArrayList<>();
        for (int row = 0; row < team.rows().size(); row++) {
            ExtractedRow extracted = team.rows().get(row);
            rows.add(new ExtractionResult.Row(extracted.nickname(), extracted.kills(), extracted.deaths(),
                    extracted.assists(), extracted.cs(), extracted.gold(), extracted.portraitBox().toList(),
                    match(image.pickIndex(top, row))));
        }
        List<ExtractionResult.Ban> bans = new ArrayList<>();
        for (int slot = 0; slot < team.banBoxes().size(); slot++) {
            bans.add(new ExtractionResult.Ban(team.banBoxes().get(slot).toList(), match(image.banIndex(top, slot))));
        }
        return new ExtractionResult.Team(team.kills(), team.deaths(), team.assists(), team.gold(), rows, bans);
    }

    private static IconMatch match(int championIndex) {
        return new IconMatch(List.of(
                new IconMatch.Candidate(FakeDataDragonSource.championId(championIndex), 0.9),
                new IconMatch.Candidate(FakeDataDragonSource.championId(championIndex + 1), 0.5),
                new IconMatch.Candidate(FakeDataDragonSource.championId(championIndex + 2), 0.4)), 0.4, false);
    }
}
