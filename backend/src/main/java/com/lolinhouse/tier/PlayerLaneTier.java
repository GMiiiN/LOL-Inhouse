package com.lolinhouse.tier;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.tier.LaneTier;
import com.lolinhouse.domain.tier.LastChange;
import com.lolinhouse.domain.tier.TierSub;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 역할: 선수 한 명의 한 라인 티어(현재 값) + 마지막 변경(▲▼ 표시용). 선수당 라인당 1행이고 티어가 없는 라인은 행이 없다.
 * 마지막 변경 3칸은 모두 null이거나 모두 값이다(DB CHECK). 규칙은 {@link LastChange#afterSave}.
 */
@Entity
@Table(name = "player_lane_tier")
public class PlayerLaneTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "player_id", nullable = false, updatable = false)
    private Long playerId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "position", nullable = false, updatable = false, length = 10)
    private Position position;

    @Column(name = "tier", nullable = false)
    private int tier;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "sub", nullable = false, length = 4)
    private TierSub sub;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "last_change_direction", length = 4)
    private LastChange.Direction lastChangeDirection;

    @Column(name = "last_change_from_tier")
    private Integer lastChangeFromTier;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "last_change_from_sub", length = 4)
    private TierSub lastChangeFromSub;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PlayerLaneTier() {
    }

    private PlayerLaneTier(Long playerId, Position position, LaneTier laneTier) {
        this.playerId = playerId;
        this.position = position;
        this.tier = laneTier.tier();
        this.sub = laneTier.sub();
    }

    /** 새 라인 추가. 마지막 변경은 없다(새 라인은 변동이 아니다). */
    public static PlayerLaneTier create(Long playerId, Position position, LaneTier laneTier) {
        return new PlayerLaneTier(playerId, position, laneTier);
    }

    public Long id() {
        return id;
    }

    public Long playerId() {
        return playerId;
    }

    public Position position() {
        return position;
    }

    public LaneTier laneTier() {
        return new LaneTier(tier, sub);
    }

    /** 마지막 변경. 없으면 null. */
    public LastChange lastChange() {
        return lastChangeDirection == null ? null
                : new LastChange(lastChangeDirection, new LaneTier(lastChangeFromTier, lastChangeFromSub));
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    /** 값을 바꿔 저장한다. 마지막 변경은 {@link LastChange#afterSave}로 정한다(같은 값이면 그대로). */
    public void changeTo(LaneTier next) {
        LastChange result = LastChange.afterSave(laneTier(), next, lastChange());
        this.tier = next.tier();
        this.sub = next.sub();
        this.lastChangeDirection = result == null ? null : result.direction();
        this.lastChangeFromTier = result == null ? null : result.from().tier();
        this.lastChangeFromSub = result == null ? null : result.from().sub();
    }

    /** 값만 바꾸고 마지막 변경(▲▼)은 건드리지 않는다. 가져오기 도구처럼 변동으로 치지 않을 때 쓴다. */
    public void overwrite(LaneTier next) {
        this.tier = next.tier();
        this.sub = next.sub();
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
