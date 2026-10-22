package com.lolinhouse.stats;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.lolinhouse.domain.stats.DuoCalculator;
import com.lolinhouse.domain.stats.DuoCalculator.Duo;
import com.lolinhouse.record.LoadedRecords;
import com.lolinhouse.record.PlayerNameRow;
import com.lolinhouse.record.RecordReader;
import com.lolinhouse.stats.DuoResponse.DuoEntry;
import com.lolinhouse.stats.DuoResponse.DuoPlayer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 역할: GET /api/duos 조립. LoadedRecords 한 번(쿼리 4개 고정)으로 읽고 domain.stats.DuoCalculator가 고른다. */
@Service
public class DuoService {

    static final int MIN_SETS = 3;
    static final int LIMIT = 3;

    private final RecordReader reader;

    public DuoService(RecordReader reader) {
        this.reader = reader;
    }

    @Transactional(readOnly = true)
    public DuoResponse get(LocalDate from, LocalDate to) {
        LoadedRecords loaded = reader.playerRecords(from, to);
        Map<Long, PlayerNameRow> names = loaded.names();
        var selection = DuoCalculator.select(loaded.records(), id -> names.get(id).displayName(), MIN_SETS, LIMIT);
        return new DuoResponse(MIN_SETS, entries(selection.best(), names), entries(selection.worst(), names));
    }

    private static List<DuoEntry> entries(List<Duo> duos, Map<Long, PlayerNameRow> names) {
        return duos.stream().map(d -> new DuoEntry(List.of(player(d.a(), names), player(d.b(), names)), d.sets(),
                d.wins(), d.losses(), d.winRate())).toList();
    }

    private static DuoPlayer player(long id, Map<Long, PlayerNameRow> names) {
        PlayerNameRow row = names.get(id);
        return new DuoPlayer(id, row.displayName(), row.registered());
    }
}
