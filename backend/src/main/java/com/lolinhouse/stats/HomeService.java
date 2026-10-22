package com.lolinhouse.stats;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.lolinhouse.champion.CatalogSnapshot;
import com.lolinhouse.champion.Champion;
import com.lolinhouse.champion.ChampionCatalog;
import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.stats.PlayerSetRecord;
import com.lolinhouse.record.LoadedRecords;
import com.lolinhouse.record.RecordReader;
import com.lolinhouse.record.SetView;
import com.lolinhouse.record.SetViewAssembler;
import com.lolinhouse.stats.HomeResponse.TopPick;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 역할: GET /api/home 조립(전체 기간). 최근 3세트와 라인별 최다 픽 챔피언.
 * 최다 픽 순서: 픽 수 → 승률 → 챔피언 한국어 이름. 이름은 동률일 때만 필요해서 카탈로그를 그때 처음 읽고,
 * 카탈로그가 없거나 모르는 키면 키 글자로 비교한다.
 */
@Service
public class HomeService {

    private static final int RECENT_SET_LIMIT = 3;

    private final RecordReader reader;
    private final SetViewAssembler assembler;
    private final ChampionCatalog catalog;

    public HomeService(RecordReader reader, SetViewAssembler assembler, ChampionCatalog catalog) {
        this.reader = reader;
        this.assembler = assembler;
        this.catalog = catalog;
    }

    @Transactional(readOnly = true)
    public HomeResponse get() {
        LoadedRecords loaded = reader.playerRecords(null, null);
        List<SetView> recent = new ArrayList<>(assembler.assemble(loaded.recentSetIds(RECENT_SET_LIMIT)).values());
        return new HomeResponse(recent, topPicks(loaded.records(), this::championNames));
    }

    private Map<String, String> championNames() {
        return catalog.current().map(CatalogSnapshot::champions).stream().flatMap(List::stream)
                .collect(Collectors.toMap(Champion::id, Champion::name, (a, b) -> a));
    }

    /** 5라인 키가 항상 모두 있고, 그 라인에서 픽이 없으면 null. 이름 조회는 동률일 때만 호출된다. */
    static Map<Position, TopPick> topPicks(List<PlayerSetRecord> records, Supplier<Map<String, String>> namesSupplier) {
        Supplier<Map<String, String>> names = memoize(namesSupplier);
        Collator korean = Collator.getInstance(Locale.KOREAN);
        Comparator<TopPick> order = Comparator.comparingInt(TopPick::picks).reversed()
                .thenComparing(Comparator.comparingDouble((TopPick p) -> p.wins() / (double) p.picks()).reversed())
                .thenComparing((a, b) -> korean.compare(nameOf(a, names), nameOf(b, names)))
                .thenComparing(TopPick::championKey);

        Map<Position, TopPick> result = new EnumMap<>(Position.class);
        for (Position position : Position.values()) {
            Map<String, List<PlayerSetRecord>> byChampion = records.stream().filter(r -> r.position() == position)
                    .collect(Collectors.groupingBy(PlayerSetRecord::champion));
            Optional<TopPick> best = byChampion.entrySet().stream()
                    .map(e -> new TopPick(e.getKey(), e.getValue().size(),
                            (int) e.getValue().stream().filter(PlayerSetRecord::win).count()))
                    .min(order);
            result.put(position, best.orElse(null));
        }
        return result;
    }

    private static String nameOf(TopPick pick, Supplier<Map<String, String>> names) {
        return names.get().getOrDefault(pick.championKey(), pick.championKey());
    }

    private static <T> Supplier<T> memoize(Supplier<T> supplier) {
        List<T> cache = new ArrayList<>(1);
        return () -> {
            if (cache.isEmpty()) {
                cache.add(supplier.get());
            }
            return cache.get(0);
        };
    }
}
