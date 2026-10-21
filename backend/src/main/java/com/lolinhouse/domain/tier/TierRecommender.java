package com.lolinhouse.domain.tier;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.lolinhouse.domain.Position;

/**
 * 역할: 라인 티어 조정 추천(친구 아티팩트 computeRecs 이식, 상수 그대로).
 * 라인 평균 KDA는 모든 기록의 (ΣK+ΣA)/ΣD(ΣD=0이면 2.5). 선수·라인마다 기준일 이후 기록만 모아 3경기 이상일 때
 * score = (swr-0.5)*2 + clamp(kda/laneAvg-1, -1, 1)*0.5 가 +-0.35 이상이면 한 단계 위/아래를 추천한다.
 * 기준일(changedOn)이 null이면 전체 기록, 아니면 세션 시작일이 기준일보다 뒤인 기록만 본다.
 */
public final class TierRecommender {

    public static final int MIN_GAMES = 3;
    public static final double THRESHOLD = 0.35;
    static final double DEFAULT_LANE_AVG = 2.5;

    private TierRecommender() {
    }

    public enum Direction {
        UP,
        DOWN
    }

    /** 티어가 있는 라인 하나. changedOn = 티어 값이 마지막으로 바뀐 날(없으면 null). */
    public record Lane(long playerId, String displayName, Position position, LaneTier tier, LocalDate changedOn) {
    }

    /** 한 선수가 한 세트에서 남긴 기록. date = 그 세트가 속한 내전 묶음 시작일. */
    public record Played(long playerId, Position position, LocalDate date, boolean win, int kills, int deaths,
            int assists) {
    }

    public record Recommendation(long playerId, String displayName, Position position, int games, int wins,
            double kda, double laneAvgKda, double score, Direction direction, LaneTier from, LaneTier to) {
    }

    /** |score| 내림차순. */
    public static List<Recommendation> recommend(List<Lane> lanes, List<Played> played) {
        Map<Position, Double> laneAvg = laneAverages(played);
        List<Recommendation> result = new ArrayList<>();
        for (Lane lane : lanes) {
            List<Played> mine = played.stream().filter(p -> p.playerId() == lane.playerId()
                    && p.position() == lane.position()
                    && (lane.changedOn() == null || p.date().isAfter(lane.changedOn()))).toList();
            int games = mine.size();
            if (games < MIN_GAMES) {
                continue;
            }
            int wins = (int) mine.stream().filter(Played::win).count();
            int k = mine.stream().mapToInt(Played::kills).sum();
            int d = mine.stream().mapToInt(Played::deaths).sum();
            int a = mine.stream().mapToInt(Played::assists).sum();
            double kda = (k + a) / (double) Math.max(d, 1);
            double avg = laneAvg.getOrDefault(lane.position(), DEFAULT_LANE_AVG);
            double s = score(wins, games, kda, avg);
            Direction direction = s >= THRESHOLD ? Direction.UP : s <= -THRESHOLD ? Direction.DOWN : null;
            if (direction == null) {
                continue;
            }
            Optional<LaneTier> next = step(lane.tier(), direction);
            next.ifPresent(to -> result.add(new Recommendation(lane.playerId(), lane.displayName(), lane.position(),
                    games, wins, kda, avg, s, direction, lane.tier(), to)));
        }
        result.sort(Comparator.comparingDouble((Recommendation r) -> Math.abs(r.score())).reversed());
        return result;
    }

    static double score(int wins, int games, double kda, double laneAvg) {
        double swr = (wins + 2) / (double) (games + 4);
        // 라인 평균이 0이면(그 라인 전원이 K+A=0) 비교 기준이 없어 KDA 항을 0으로 둔다.
        double ratio = laneAvg > 0 ? Math.max(-1, Math.min(1, kda / laneAvg - 1)) : 0;
        return (swr - 0.5) * 2 + ratio * 0.5;
    }

    static Map<Position, Double> laneAverages(List<Played> played) {
        Map<Position, int[]> sums = new EnumMap<>(Position.class);
        for (Played p : played) {
            int[] s = sums.computeIfAbsent(p.position(), k -> new int[2]);
            s[0] += p.kills() + p.assists();
            s[1] += p.deaths();
        }
        Map<Position, Double> avg = new EnumMap<>(Position.class);
        sums.forEach((position, s) -> avg.put(position, s[1] == 0 ? DEFAULT_LANE_AVG : s[0] / (double) s[1]));
        return avg;
    }

    /** 한 단계 이동. 1티어 상에서 위로, 6티어 하에서 아래로는 없다. */
    static Optional<LaneTier> step(LaneTier tier, Direction direction) {
        if (direction == Direction.UP) {
            return switch (tier.sub()) {
                case LOW -> Optional.of(new LaneTier(tier.tier(), TierSub.MID));
                case MID -> Optional.of(new LaneTier(tier.tier(), TierSub.HIGH));
                case HIGH -> tier.tier() == LaneTier.MIN_TIER ? Optional.empty()
                        : Optional.of(new LaneTier(tier.tier() - 1, TierSub.LOW));
            };
        }
        return switch (tier.sub()) {
            case HIGH -> Optional.of(new LaneTier(tier.tier(), TierSub.MID));
            case MID -> Optional.of(new LaneTier(tier.tier(), TierSub.LOW));
            case LOW -> tier.tier() == LaneTier.MAX_TIER ? Optional.empty()
                    : Optional.of(new LaneTier(tier.tier() + 1, TierSub.HIGH));
        };
    }
}
