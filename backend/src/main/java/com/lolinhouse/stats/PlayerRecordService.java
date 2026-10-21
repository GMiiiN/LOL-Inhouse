package com.lolinhouse.stats;

import java.text.Collator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.lolinhouse.common.web.ApiException;
import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.stats.DuelPersonalResult;
import com.lolinhouse.domain.stats.DuelResult;
import com.lolinhouse.domain.stats.PlayTotals;
import com.lolinhouse.domain.stats.PlayerSetRecord;
import com.lolinhouse.domain.stats.PlayerStats;
import com.lolinhouse.domain.stats.StatSummary;
import com.lolinhouse.record.LoadedRecords;
import com.lolinhouse.record.LoadedRecords.SetMeta;
import com.lolinhouse.record.PlayerNameRow;
import com.lolinhouse.record.RecordReader;
import com.lolinhouse.record.SetView;
import com.lolinhouse.record.SetViewAssembler;
import com.lolinhouse.stats.PlayerListResponse.TopChampion;
import com.lolinhouse.stats.PlayerRecordResponse.ChampionSummary;
import com.lolinhouse.stats.PlayerRecordResponse.DuelResults;
import com.lolinhouse.stats.PlayerRecordResponse.LaneTierView;
import com.lolinhouse.stats.PlayerRecordResponse.PositionSummary;
import com.lolinhouse.tier.PlayerLaneTier;
import com.lolinhouse.tier.PlayerLaneTierRepository;
import com.lolinhouse.tier.TierListResponse.Trend;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 역할: 선수 목록(GET /api/players)과 선수 프로필(GET /api/players/{id}) 조립.
 * 통계는 RecordReader가 읽은 기간 전체 기록에서 M2 domain.stats로 계산하고, 조회 트랜잭션 하나에서 같은 스냅샷을 본다.
 * 선수 존재는 경기 기록과 별개로 확인한다(없는 id만 404, 기록이 없으면 빈 통계).
 */
@Service
public class PlayerRecordService {

    private static final int TOP_CHAMPION_LIMIT = 5;
    private static final int RECENT_SET_LIMIT = 5;

    private final RecordReader reader;
    private final SetViewAssembler assembler;
    private final PlayerLaneTierRepository laneTiers;

    public PlayerRecordService(RecordReader reader, SetViewAssembler assembler, PlayerLaneTierRepository laneTiers) {
        this.reader = reader;
        this.assembler = assembler;
        this.laneTiers = laneTiers;
    }

    /** 대상 = 등록 선수 전체 + 기간 안에 뛴 선수. 경기 수 내림차순, 같으면 이름(한국어) 순. */
    @Transactional(readOnly = true)
    public PlayerListResponse list(LocalDate from, LocalDate to) {
        LoadedRecords loaded = reader.playerRecords(from, to);
        Map<Long, PlayerNameRow> names = new LinkedHashMap<>(reader.registeredNames());
        names.putAll(loaded.names());
        Map<String, List<PlayerSetRecord>> byPlayer = loaded.records().stream()
                .collect(Collectors.groupingBy(PlayerSetRecord::playerId));

        Collator korean = Collator.getInstance(Locale.KOREAN);
        List<PlayerListResponse.Item> items = names.values().stream()
                .map(name -> item(name, byPlayer.getOrDefault(String.valueOf(name.id()), List.of())))
                .sorted(Comparator.comparingInt((PlayerListResponse.Item item) -> item.summary().sets()).reversed()
                        .thenComparing(PlayerListResponse.Item::displayName, korean)
                        .thenComparingLong(PlayerListResponse.Item::playerId))
                .toList();
        return new PlayerListResponse(loaded.totalSets(), items);
    }

    @Transactional(readOnly = true)
    public PlayerRecordResponse profile(long playerId, LocalDate from, LocalDate to) {
        PlayerNameRow name = reader.namesOf(List.of(playerId)).get(playerId);
        if (name == null) {
            throw ApiException.notFound("PLAYER_NOT_FOUND", "선수를 찾을 수 없습니다: " + playerId);
        }
        String id = String.valueOf(playerId);
        LoadedRecords loaded = reader.playerRecords(from, to);
        List<PlayerSetRecord> mine = loaded.records().stream().filter(r -> r.playerId().equals(id)).toList();

        List<SetView> recentSets = new ArrayList<>(
                assembler.assemble(loaded.recentSetIdsOfPlayer(playerId, RECENT_SET_LIMIT)).values());
        return new PlayerRecordResponse(playerId, name.displayName(), name.registered(), name.mainPosition(),
                laneTierViews(playerId), loaded.totalSets(),
                PlayerStats.participationRate(mine, id, loaded.totalSets()), summary(mine),
                positionSummaries(mine, id), championSummaries(mine, id), duelResults(mine, id, loaded), recentSets);
    }

    private PlayerListResponse.Item item(PlayerNameRow name, List<PlayerSetRecord> mine) {
        Map<Position, Integer> positions = new EnumMap<>(Position.class);
        mine.forEach(r -> positions.merge(r.position(), 1, Integer::sum));
        String id = String.valueOf(name.id());
        return new PlayerListResponse.Item(name.id(), name.displayName(), name.registered(), name.mainPosition(),
                summary(mine), positions, topChampions(mine, id), PlayerStats.byChampion(mine, id).size());
    }

    private static SummaryView summary(List<PlayerSetRecord> records) {
        return SummaryView.from(StatSummary.of(records), PlayTotals.of(records));
    }

    private static List<TopChampion> topChampions(List<PlayerSetRecord> records, String playerId) {
        return PlayerStats.byChampion(records, playerId).stream().limit(TOP_CHAMPION_LIMIT)
                .map(c -> new TopChampion(c.champion(), c.summary().sets())).toList();
    }

    /** 5라인 키가 모두 있고 티어가 없는 라인은 null. 점수는 공개하지 않는다. */
    private Map<Position, LaneTierView> laneTierViews(long playerId) {
        Map<Position, LaneTierView> views = new EnumMap<>(Position.class);
        for (Position position : Position.values()) {
            views.put(position, null);
        }
        for (PlayerLaneTier lane : laneTiers.findByPlayerId(playerId)) {
            var change = lane.lastChange();
            Trend trend = change == null ? null
                    : new Trend(change.direction(), change.from().tier(), change.from().sub());
            views.put(lane.position(), new LaneTierView(lane.laneTier().tier(), lane.laneTier().sub(), trend));
        }
        return views;
    }

    /** 경기 수가 많은 라인부터. */
    private static List<PositionSummary> positionSummaries(List<PlayerSetRecord> mine, String id) {
        Map<Position, List<PlayerSetRecord>> byPosition = new EnumMap<>(Position.class);
        mine.forEach(r -> byPosition.computeIfAbsent(r.position(), p -> new ArrayList<>()).add(r));
        return byPosition.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<Position, List<PlayerSetRecord>> e) -> e.getValue().size())
                        .reversed().thenComparing(Map.Entry::getKey))
                .map(e -> new PositionSummary(e.getKey(), summary(e.getValue()), topChampions(e.getValue(), id)))
                .toList();
    }

    /** 사용 횟수순(같으면 키 순, PlayerStats.byChampion 순서 그대로). */
    private static List<ChampionSummary> championSummaries(List<PlayerSetRecord> mine, String id) {
        Map<String, List<PlayerSetRecord>> byChampion = mine.stream()
                .collect(Collectors.groupingBy(PlayerSetRecord::champion));
        return PlayerStats.byChampion(mine, id).stream()
                .map(c -> new ChampionSummary(c.champion(), summary(byChampion.get(c.champion()))))
                .toList();
    }

    private static DuelResults duelResults(List<PlayerSetRecord> mine, String id, LoadedRecords loaded) {
        Map<Long, SetMeta> metaByDuel = new LinkedHashMap<>();
        loaded.setMeta().values().forEach(meta -> metaByDuel.putIfAbsent(meta.duelId(), meta));

        record Row(long duelId, SetMeta meta, DuelPersonalResult result) {
        }
        List<Row> rows = new ArrayList<>();
        for (String duelId : mine.stream().map(PlayerSetRecord::duelId).distinct().toList()) {
            long duel = Long.parseLong(duelId);
            rows.add(new Row(duel, metaByDuel.get(duel), PlayerStats.duelResult(mine, id, duelId)));
        }
        rows.sort(Comparator.comparing((Row r) -> r.meta().startDate()).thenComparingInt(r -> r.meta().duelSeq())
                .reversed());

        int wins = 0;
        int draws = 0;
        int losses = 0;
        List<PlayerRecordResponse.Item> items = new ArrayList<>();
        for (Row row : rows) {
            DuelResult result = row.result().result();
            switch (result) {
                case WIN -> wins++;
                case DRAW -> draws++;
                case LOSS -> losses++;
            }
            items.add(new PlayerRecordResponse.Item(row.duelId(), row.meta().startDate(), row.meta().duelSeq(),
                    row.result().wins(), row.result().losses(), result));
        }
        return new DuelResults(wins, draws, losses, items);
    }
}
