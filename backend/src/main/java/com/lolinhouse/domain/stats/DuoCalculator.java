package com.lolinhouse.domain.stats;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.LongFunction;

/**
 * 역할: 같은 세트에서 같은 팀이었던 두 선수(듀오)의 함께 뛴 세트·승패를 세고 최고/최악을 고른다.
 * 최고 = 승률 내림차순 → 세트 수 내림차순 → 이름, 최악 = 승률 오름차순 → 세트 수 내림차순 → 이름.
 * 한 쌍이 최고와 최악에 동시에 들어가지 않는다(최악은 최고에 뽑힌 쌍을 뺀 나머지에서 고른다).
 */
public final class DuoCalculator {

    private DuoCalculator() {
    }

    /** a < b (선수 id 오름차순). */
    public record Duo(long a, long b, int sets, int wins) {
        public int losses() {
            return sets - wins;
        }

        public double winRate() {
            return wins / (double) sets;
        }
    }

    public record Selection(List<Duo> best, List<Duo> worst) {
    }

    /** 모든 듀오의 집계. 함께 뛴 세트 수 조건은 걸지 않는다. */
    public static List<Duo> pairs(List<PlayerSetRecord> records) {
        Map<String, List<PlayerSetRecord>> teams = new HashMap<>();
        for (PlayerSetRecord r : records) {
            teams.computeIfAbsent(r.setId() + "/" + r.team(), k -> new ArrayList<>()).add(r);
        }
        Map<List<Long>, int[]> counts = new HashMap<>();
        for (List<PlayerSetRecord> team : teams.values()) {
            for (int i = 0; i < team.size(); i++) {
                for (int j = i + 1; j < team.size(); j++) {
                    long x = Long.parseLong(team.get(i).playerId());
                    long y = Long.parseLong(team.get(j).playerId());
                    if (x == y) {
                        continue;
                    }
                    int[] c = counts.computeIfAbsent(List.of(Math.min(x, y), Math.max(x, y)), k -> new int[2]);
                    c[0]++;
                    if (team.get(i).win()) {
                        c[1]++;
                    }
                }
            }
        }
        List<Duo> duos = new ArrayList<>();
        counts.forEach((k, c) -> duos.add(new Duo(k.get(0), k.get(1), c[0], c[1])));
        return duos;
    }

    public static Selection select(List<PlayerSetRecord> records, LongFunction<String> nameOf, int minSets, int limit) {
        Collator korean = Collator.getInstance(Locale.KOREAN);
        Comparator<Duo> byName = (x, y) -> {
            int first = korean.compare(nameOf.apply(x.a()), nameOf.apply(y.a()));
            return first != 0 ? first : korean.compare(nameOf.apply(x.b()), nameOf.apply(y.b()));
        };
        Comparator<Duo> bySetsDesc = Comparator.comparingInt(Duo::sets).reversed();
        // 승률은 분수 교차곱으로 비교해 부동소수 오차 없이 동률을 잡는다.
        Comparator<Duo> winRateAsc = (x, y) -> Long.compare((long) x.wins() * y.sets(), (long) y.wins() * x.sets());
        List<Duo> eligible = pairs(records).stream().filter(d -> d.sets() >= minSets).toList();

        List<Duo> best = eligible.stream()
                .sorted(winRateAsc.reversed().thenComparing(bySetsDesc).thenComparing(byName)).limit(limit).toList();
        Set<Duo> taken = new HashSet<>(best);
        List<Duo> worst = eligible.stream().filter(d -> !taken.contains(d))
                .sorted(winRateAsc.thenComparing(bySetsDesc).thenComparing(byName)).limit(limit).toList();
        return new Selection(best, worst);
    }
}
