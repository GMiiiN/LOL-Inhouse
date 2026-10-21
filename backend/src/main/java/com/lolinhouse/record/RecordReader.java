package com.lolinhouse.record;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.lolinhouse.domain.stats.ChampionPickBan.BannedChampion;
import com.lolinhouse.domain.stats.PlayerSetRecord;
import com.lolinhouse.record.LoadedRecords.SetMeta;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 역할: 공개 조회 전용 로더. 프로젝션(JPQL 생성자 표현식)만 쓰고 엔티티의 지연 로딩(GameSet.players)은 쓰지 않아 N+1이 없다.
 * 모든 쿼리는 삭제된 세트(deleted_at)를 뺀다. 쿼리 수는 읽는 세트 수와 무관하게 메서드마다 고정이다.
 * 읽기 전용 트랜잭션 안에서 여러 메서드를 부르면 같은 스냅샷을 본다(호출자가 @Transactional(readOnly = true)을 걸 것).
 * 설명: docs/design/m5-design.md §2.1, §10-3
 */
@Component
public class RecordReader {

    private static final String SET_SELECT = "select new com.lolinhouse.record.SetRow("
            + "g.id, g.duelId, g.setNo, g.team1Side, g.winnerTeam, g.shotDate, g.durationSec, "
            + "g.team1DisplayedKills, g.team1DisplayedDeaths, g.team1DisplayedAssists, g.team1DisplayedGold, "
            + "g.team2DisplayedKills, g.team2DisplayedDeaths, g.team2DisplayedAssists, g.team2DisplayedGold, "
            + "d.seq, d.fearlessMode, s.id, s.startDate) "
            + "from GameSet g join Duel d on d.id = g.duelId join InhouseSession s on s.id = d.sessionId "
            + "where g.deletedAt is null";

    private static final String PLAYER_SELECT = "select new com.lolinhouse.record.PlayerRow("
            + "g.id, p.playerId, p.team, p.position, p.championKey, p.kills, p.deaths, p.assists, p.cs, p.gold) "
            + "from GameSetPlayer p join p.gameSet g join Duel d on d.id = g.duelId "
            + "join InhouseSession s on s.id = d.sessionId where g.deletedAt is null";

    private static final String BAN_SELECT =
            "select new com.lolinhouse.record.BanRow(g.id, b.turn, b.side, b.championKey) "
            + "from GameSetBan b join b.gameSet g join Duel d on d.id = g.duelId "
            + "join InhouseSession s on s.id = d.sessionId where g.deletedAt is null";

    private static final String NAME_SELECT = "select new com.lolinhouse.record.PlayerNameRow("
            + "p.id, p.gameNickname, p.realName, p.mainPosition) from Player p";

    private static final String RECENT_ORDER = " order by s.startDate desc, d.seq desc, g.setNo desc";

    @PersistenceContext
    private EntityManager em;

    /** 시작일 기준 기간(양끝 포함, null = 제한 없음)의 유효 세트, 최근 것부터(시작일, 대결 seq, 세트 번호 내림차순). 쿼리 1개. */
    @Transactional(readOnly = true)
    public List<SetRow> setsInPeriod(LocalDate from, LocalDate to) {
        return run(SET_SELECT + periodClause(from, to) + RECENT_ORDER, SetRow.class, from, to);
    }

    /** id로 고른 유효 세트(없거나 삭제된 id는 결과에 없음), 최근 것부터. 쿼리 1개. */
    @Transactional(readOnly = true)
    public List<SetRow> setsByIds(Collection<Long> setIds) {
        if (setIds.isEmpty()) {
            return List.of();
        }
        return em.createQuery(SET_SELECT + " and g.id in :ids" + RECENT_ORDER, SetRow.class)
                .setParameter("ids", setIds).getResultList();
    }

    /** 세트들의 선수 행. 쿼리 1개. */
    @Transactional(readOnly = true)
    public List<PlayerRow> playersOfSets(Collection<Long> setIds) {
        if (setIds.isEmpty()) {
            return List.of();
        }
        return em.createQuery(PLAYER_SELECT + " and g.id in :ids", PlayerRow.class)
                .setParameter("ids", setIds).getResultList();
    }

    /** 세트들의 밴 행(세트 id, turn 오름차순). 쿼리 1개. */
    @Transactional(readOnly = true)
    public List<BanRow> bansOfSets(Collection<Long> setIds) {
        if (setIds.isEmpty()) {
            return List.of();
        }
        return em.createQuery(BAN_SELECT + " and g.id in :ids order by g.id, b.turn", BanRow.class)
                .setParameter("ids", setIds).getResultList();
    }

    /** 선수 이름. 없는 id는 결과에 없다. 쿼리 1개. */
    @Transactional(readOnly = true)
    public Map<Long, PlayerNameRow> namesOf(Collection<Long> playerIds) {
        if (playerIds.isEmpty()) {
            return Map.of();
        }
        return toNameMap(em.createQuery(NAME_SELECT + " where p.id in :ids", PlayerNameRow.class)
                .setParameter("ids", playerIds).getResultList());
    }

    /** 실명이 있는 등록 선수 전체(기록이 없어도 선수 목록에 나와야 한다). 쿼리 1개. */
    @Transactional(readOnly = true)
    public Map<Long, PlayerNameRow> registeredNames() {
        return toNameMap(em.createQuery(NAME_SELECT + " where p.realName is not null", PlayerNameRow.class)
                .getResultList());
    }

    /** 묶음들의 대결 전부(체인 계산용, 세트가 없는 대결 포함). 쿼리 1개. */
    @Transactional(readOnly = true)
    public List<DuelRow> duelsOfSessions(Collection<Long> sessionIds) {
        if (sessionIds.isEmpty()) {
            return List.of();
        }
        return em.createQuery("select new com.lolinhouse.record.DuelRow(d.id, d.sessionId, d.seq, d.fearlessMode) "
                + "from Duel d where d.sessionId in :ids order by d.sessionId, d.seq", DuelRow.class)
                .setParameter("ids", sessionIds).getResultList();
    }

    /** 대결들의 유효 세트 픽 전부(세트 번호 포함). 피어리스 제한 계산 입력. 쿼리 1개. */
    @Transactional(readOnly = true)
    public List<PickRow> picksOfDuels(Collection<Long> duelIds) {
        if (duelIds.isEmpty()) {
            return List.of();
        }
        return em.createQuery("select new com.lolinhouse.record.PickRow(g.id, g.duelId, g.setNo, p.championKey) "
                + "from GameSetPlayer p join p.gameSet g where g.duelId in :ids and g.deletedAt is null",
                PickRow.class).setParameter("ids", duelIds).getResultList();
    }

    /** 기간 안의 모든 유효 세트를 통계 입력으로 읽는다. 쿼리 4개(세트, 선수, 밴, 이름)로 고정. */
    @Transactional(readOnly = true)
    public LoadedRecords playerRecords(LocalDate from, LocalDate to) {
        String period = periodClause(from, to);
        List<SetRow> sets = run(SET_SELECT + period + RECENT_ORDER, SetRow.class, from, to);
        List<PlayerRow> players = run(PLAYER_SELECT + period, PlayerRow.class, from, to);
        List<BanRow> bans = run(BAN_SELECT + period, BanRow.class, from, to);

        Map<Long, SetRow> setById = new HashMap<>();
        Map<Long, SetMeta> setMeta = new LinkedHashMap<>();
        for (SetRow set : sets) {
            setById.put(set.setId(), set);
            setMeta.put(set.setId(), new SetMeta(set.startDate(), set.duelId(), set.duelSeq(), set.setNo()));
        }
        List<PlayerSetRecord> records = new ArrayList<>(players.size());
        for (PlayerRow row : players) {
            SetRow set = setById.get(row.setId());
            records.add(new PlayerSetRecord(String.valueOf(row.playerId()), String.valueOf(set.duelId()),
                    String.valueOf(row.setId()), row.team(), row.position(), row.championKey(),
                    row.team() == set.winnerTeam(), row.kills(), row.deaths(), row.assists(), row.cs(), row.gold()));
        }
        List<BannedChampion> bannedChampions = bans.stream()
                .map(b -> new BannedChampion(String.valueOf(b.setId()), b.championKey())).toList();
        Map<Long, PlayerNameRow> names = namesOf(players.stream().map(PlayerRow::playerId).distinct().toList());
        return new LoadedRecords(records, names, sets.size(), setMeta, bannedChampions);
    }

    private static Map<Long, PlayerNameRow> toNameMap(List<PlayerNameRow> rows) {
        Map<Long, PlayerNameRow> map = new LinkedHashMap<>();
        for (PlayerNameRow row : rows) {
            map.put(row.id(), row);
        }
        return map;
    }

    private static String periodClause(LocalDate from, LocalDate to) {
        return (from != null ? " and s.startDate >= :from" : "") + (to != null ? " and s.startDate <= :to" : "");
    }

    private <T> List<T> run(String jpql, Class<T> type, LocalDate from, LocalDate to) {
        var query = em.createQuery(jpql, type);
        if (from != null) {
            query.setParameter("from", from);
        }
        if (to != null) {
            query.setParameter("to", to);
        }
        return query.getResultList();
    }
}
