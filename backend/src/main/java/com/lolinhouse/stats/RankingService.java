package com.lolinhouse.stats;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.stats.PlayTotals;
import com.lolinhouse.domain.stats.PlayerSetRecord;
import com.lolinhouse.domain.stats.RankingCalculator;
import com.lolinhouse.domain.stats.RankingEntry;
import com.lolinhouse.domain.stats.RankingKey;
import com.lolinhouse.record.LoadedRecords;
import com.lolinhouse.record.PlayerNameRow;
import com.lolinhouse.record.RecordReader;
import com.lolinhouse.stats.RankingResponse.Entry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 역할: GET /api/rankings 조립. 순위·정렬·최소 표본(5세트, 포지션 탭은 그 포지션 5세트)은 M2 RankingCalculator가 정하고,
 * 여기서는 도메인의 순위 없음(UNRANKED 0)을 API의 rank null로 바꾸고 이름·CS/골드 합계를 붙인다.
 */
@Service
public class RankingService {

    private final RecordReader reader;

    public RankingService(RecordReader reader) {
        this.reader = reader;
    }

    /** position이 null이면 전체 탭. */
    @Transactional(readOnly = true)
    public RankingResponse rank(Position position, RankingKey sort, LocalDate from, LocalDate to) {
        LoadedRecords loaded = reader.playerRecords(from, to);
        List<RankingEntry> ranked = position == null
                ? RankingCalculator.overall(loaded.records(), RankingCalculator.DEFAULT_MIN_SETS, sort)
                : RankingCalculator.byPosition(loaded.records(), position, RankingCalculator.DEFAULT_MIN_SETS, sort);
        Map<String, List<PlayerSetRecord>> byPlayer = loaded.records().stream()
                .filter(r -> position == null || r.position() == position)
                .collect(Collectors.groupingBy(PlayerSetRecord::playerId));

        List<Entry> entries = ranked.stream().map(e -> {
            long id = Long.parseLong(e.playerId());
            PlayerNameRow name = loaded.names().get(id);
            return new Entry(id, name != null ? name.displayName() : "#" + id, name != null && name.registered(),
                    rankOrNull(e.rank()), SummaryView.from(e.summary(), PlayTotals.of(byPlayer.get(e.playerId()))));
        }).toList();
        return new RankingResponse(position == null ? StatsQuery.ALL : position.name(), sort,
                RankingCalculator.DEFAULT_MIN_SETS, loaded.totalSets(), entries);
    }

    /** 도메인의 UNRANKED(0) = 표본 미달 → null. */
    static Integer rankOrNull(int domainRank) {
        return domainRank == RankingEntry.UNRANKED ? null : domainRank;
    }
}
