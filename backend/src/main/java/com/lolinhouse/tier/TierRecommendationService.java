package com.lolinhouse.tier;

import java.util.List;
import java.util.Map;

import com.lolinhouse.domain.tier.TierRecommender;
import com.lolinhouse.domain.tier.TierRecommender.Lane;
import com.lolinhouse.domain.tier.TierRecommender.Played;
import com.lolinhouse.domain.tier.TierRecommender.Recommendation;
import com.lolinhouse.record.LoadedRecords;
import com.lolinhouse.record.PlayerNameRow;
import com.lolinhouse.record.RecordReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 역할: GET /api/admin/tier-recommendations 조립. 전체 기록(쿼리 4개)과 라인 티어 전체(쿼리 1개)를 읽어 domain이 계산한다. */
@Service
public class TierRecommendationService {

    private final RecordReader reader;
    private final PlayerLaneTierRepository laneTiers;

    public TierRecommendationService(RecordReader reader, PlayerLaneTierRepository laneTiers) {
        this.reader = reader;
        this.laneTiers = laneTiers;
    }

    @Transactional(readOnly = true)
    public List<Recommendation> recommend() {
        LoadedRecords loaded = reader.playerRecords(null, null);
        Map<Long, PlayerNameRow> names = loaded.names();
        List<Played> played = loaded.records().stream().map(r -> new Played(Long.parseLong(r.playerId()),
                r.position(), loaded.setMeta().get(Long.parseLong(r.setId())).startDate(), r.win(), r.kills(),
                r.deaths(), r.assists())).toList();
        // 기록이 없는 선수는 어차피 3경기 미만이라 이름이 없어도 된다(names에 없는 선수는 건너뛴다).
        List<Lane> lanes = laneTiers.findAll().stream().filter(l -> names.containsKey(l.playerId()))
                .map(l -> new Lane(l.playerId(), names.get(l.playerId()).displayName(), l.position(), l.laneTier(),
                        l.tierChangedOn())).toList();
        return TierRecommender.recommend(lanes, played);
    }
}
