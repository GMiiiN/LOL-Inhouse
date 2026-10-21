package com.lolinhouse.tier;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.tier.ScoreTable;
import com.lolinhouse.domain.tier.TierSub;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * 역할: 점수표의 현재 값(id = 1 한 행, V4가 시드). 이 행은 점수표 저장뿐 아니라 프로필·선수 생성/수정의 전역 직렬화 잠금으로도 쓴다(설계 §11).
 */
@Entity
@Table(name = "score_setting")
public class ScoreSetting {

    /** 유일한 행의 id. */
    public static final int SINGLE_ID = 1;

    @Id
    private Integer id;

    @Column(name = "base_tier1", nullable = false)
    private int baseTier1;
    @Column(name = "base_tier2", nullable = false)
    private int baseTier2;
    @Column(name = "base_tier3", nullable = false)
    private int baseTier3;
    @Column(name = "base_tier4", nullable = false)
    private int baseTier4;
    @Column(name = "base_tier5", nullable = false)
    private int baseTier5;
    @Column(name = "base_tier6", nullable = false)
    private int baseTier6;
    @Column(name = "sub_high", nullable = false)
    private int subHigh;
    @Column(name = "sub_mid", nullable = false)
    private int subMid;
    @Column(name = "sub_low", nullable = false)
    private int subLow;
    @Column(name = "lane_top", nullable = false)
    private int laneTop;
    @Column(name = "lane_jungle", nullable = false)
    private int laneJungle;
    @Column(name = "lane_mid", nullable = false)
    private int laneMid;
    @Column(name = "lane_adc", nullable = false)
    private int laneAdc;
    @Column(name = "lane_support", nullable = false)
    private int laneSupport;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ScoreSetting() {
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public ScoreTable toTable() {
        Map<TierSub, Integer> sub = new EnumMap<>(TierSub.class);
        sub.put(TierSub.HIGH, subHigh);
        sub.put(TierSub.MID, subMid);
        sub.put(TierSub.LOW, subLow);
        Map<Position, Integer> lane = new EnumMap<>(Position.class);
        lane.put(Position.TOP, laneTop);
        lane.put(Position.JUNGLE, laneJungle);
        lane.put(Position.MID, laneMid);
        lane.put(Position.ADC, laneAdc);
        lane.put(Position.SUPPORT, laneSupport);
        return new ScoreTable(List.of(baseTier1, baseTier2, baseTier3, baseTier4, baseTier5, baseTier6), sub, lane);
    }

    /** 14칸을 table 값으로 덮어쓴다. */
    public void apply(ScoreTable table) {
        baseTier1 = table.baseOf(1);
        baseTier2 = table.baseOf(2);
        baseTier3 = table.baseOf(3);
        baseTier4 = table.baseOf(4);
        baseTier5 = table.baseOf(5);
        baseTier6 = table.baseOf(6);
        subHigh = table.sub().get(TierSub.HIGH);
        subMid = table.sub().get(TierSub.MID);
        subLow = table.sub().get(TierSub.LOW);
        laneTop = table.lane().get(Position.TOP);
        laneJungle = table.lane().get(Position.JUNGLE);
        laneMid = table.lane().get(Position.MID);
        laneAdc = table.lane().get(Position.ADC);
        laneSupport = table.lane().get(Position.SUPPORT);
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
