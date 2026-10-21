package com.lolinhouse.record.export;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.lolinhouse.champion.CatalogSnapshot;
import com.lolinhouse.champion.Champion;
import com.lolinhouse.champion.ChampionCatalog;
import com.lolinhouse.record.BanRow;
import com.lolinhouse.record.PlayerNameRow;
import com.lolinhouse.record.PlayerRow;
import com.lolinhouse.record.RecordFilter;
import com.lolinhouse.record.RecordReader;
import com.lolinhouse.record.SetRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 역할: 필터에 맞는 세트를 읽어 CSV 바이트로 만든다. 읽기 전용 트랜잭션 하나에서 같은 스냅샷을 본다. */
@Service
public class RecordExportService {

    private final RecordReader reader;
    private final ChampionCatalog catalog;

    public RecordExportService(RecordReader reader, ChampionCatalog catalog) {
        this.reader = reader;
        this.catalog = catalog;
    }

    @Transactional(readOnly = true)
    public byte[] export(RecordFilter filter) {
        List<SetRow> periodSets = reader.setsInPeriod(filter.from(), filter.to());
        List<Long> periodIds = periodSets.stream().map(SetRow::setId).toList();
        Map<Long, List<PlayerRow>> playersBySet = reader.playersOfSets(periodIds).stream()
                .collect(Collectors.groupingBy(PlayerRow::setId));
        Map<Long, List<BanRow>> bansBySet = reader.bansOfSets(periodIds).stream()
                .collect(Collectors.groupingBy(BanRow::setId));

        List<SetRow> sets = periodSets.stream().filter(s -> filter.matches(s.startDate(),
                playersBySet.getOrDefault(s.setId(), List.of()), bansBySet.getOrDefault(s.setId(), List.of())))
                .toList();
        List<PlayerRow> players = sets.stream()
                .flatMap(s -> playersBySet.getOrDefault(s.setId(), List.of()).stream()).toList();
        List<BanRow> bans = sets.stream()
                .flatMap(s -> bansBySet.getOrDefault(s.setId(), List.of()).stream()).toList();
        Map<Long, PlayerNameRow> names = reader.namesOf(players.stream().map(PlayerRow::playerId).distinct().toList());

        return RecordCsvWriter.write(sets, players, bans, names, championNames());
    }

    /** 요청 하나에서 카탈로그 스냅샷을 한 번만 쓴다. 없으면 모든 키를 그대로 내보낸다. */
    private Function<String, String> championNames() {
        Map<String, String> byId = catalog.current().map(CatalogSnapshot::champions).orElse(List.of()).stream()
                .collect(Collectors.toMap(Champion::id, Champion::name, (a, b) -> a));
        return byId::get;
    }
}
