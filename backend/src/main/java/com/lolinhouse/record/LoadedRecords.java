package com.lolinhouse.record;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.lolinhouse.domain.stats.ChampionPickBan.BannedChampion;
import com.lolinhouse.domain.stats.PlayerSetRecord;

/**
 * 역할: 기간 안의 유효 세트를 한 번에 읽은 통계 입력. 통계 계산(M2 domain.stats)과 "최근 N세트" 판별이 같은 값을 본다.
 * records = 선수×세트 행(승패 계산 끝), names = 기간 안에 뛴 선수의 이름(등록 선수 전체가 필요하면 RecordReader.registeredNames),
 * totalSets = 기간 안의 유효 세트 수, setMeta = setId → 시작일·대결·세트 번호, bans = (setId, 챔피언 키).
 */
public record LoadedRecords(
        List<PlayerSetRecord> records,
        Map<Long, PlayerNameRow> names,
        int totalSets,
        Map<Long, SetMeta> setMeta,
        List<BannedChampion> bans) {

    /** 세트가 속한 위치. 최근 순서 = 시작일 → 대결 seq → 세트 번호 내림차순. */
    public record SetMeta(LocalDate startDate, long duelId, int duelSeq, int setNo) {
    }

    private static final Comparator<Map.Entry<Long, SetMeta>> RECENT_FIRST = Comparator
            .comparing((Map.Entry<Long, SetMeta> e) -> e.getValue().startDate())
            .thenComparingInt(e -> e.getValue().duelSeq())
            .thenComparingInt(e -> e.getValue().setNo())
            .reversed();

    /** 가장 최근 세트 id부터 limit개. */
    public List<Long> recentSetIds(int limit) {
        return setMeta.entrySet().stream().sorted(RECENT_FIRST).limit(limit).map(Map.Entry::getKey).toList();
    }

    /** 그 선수가 뛴 세트 중 가장 최근 것부터 limit개. */
    public List<Long> recentSetIdsOfPlayer(long playerId, int limit) {
        String id = String.valueOf(playerId);
        Set<Long> played = records.stream().filter(r -> r.playerId().equals(id))
                .map(r -> Long.valueOf(r.setId())).collect(Collectors.toSet());
        return setMeta.entrySet().stream().filter(e -> played.contains(e.getKey())).sorted(RECENT_FIRST)
                .limit(limit).map(Map.Entry::getKey).toList();
    }
}
