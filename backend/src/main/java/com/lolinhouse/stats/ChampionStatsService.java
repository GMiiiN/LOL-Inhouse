package com.lolinhouse.stats;

import java.time.LocalDate;

import com.lolinhouse.domain.stats.ChampionPickBan;
import com.lolinhouse.record.LoadedRecords;
import com.lolinhouse.record.RecordReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 역할: GET /api/champion-stats 조립. 픽·밴 집계는 ChampionPickBan, 정렬은 프런트. 밴픽률 분모(totalSets)는 기간 안 유효 세트 수. */
@Service
public class ChampionStatsService {

    private final RecordReader reader;

    public ChampionStatsService(RecordReader reader) {
        this.reader = reader;
    }

    @Transactional(readOnly = true)
    public ChampionStatsResponse get(LocalDate from, LocalDate to) {
        LoadedRecords loaded = reader.playerRecords(from, to);
        var items = ChampionPickBan.compute(loaded.records(), loaded.bans()).stream()
                .map(c -> new ChampionStatsResponse.Item(c.champion(), c.picks(), c.bans(), c.wins(), c.losses(),
                        c.setsWithPickOrBan()))
                .toList();
        return new ChampionStatsResponse(loaded.totalSets(), items);
    }
}
