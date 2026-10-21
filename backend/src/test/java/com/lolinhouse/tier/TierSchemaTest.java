package com.lolinhouse.tier;

import java.time.Duration;
import java.time.Instant;

import com.lolinhouse.PersistenceTest;
import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.tier.LaneTier;
import com.lolinhouse.domain.tier.LastChange;
import com.lolinhouse.domain.tier.ScoreTable;
import com.lolinhouse.domain.tier.TierSub;
import com.lolinhouse.player.Player;
import com.lolinhouse.player.PlayerRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 역할: V4 스키마(player_lane_tier, score_setting)와 엔티티 매핑을 확인한다. Phase 0에서 설계의 [확인 필요] 세 가지를 판정한다.
 * (1) MySQL 3823: FK 컬럼(player_id)을 CHECK에 쓰지 않으면 CASCADE FK와 CHECK가 한 테이블에 함께 있어도 마이그레이션이 통과한다(컨텍스트가 뜨는 것이 증거).
 * (2) INT 컬럼과 Java int/Integer 매핑이 Hibernate validate를 통과하고 값이 왕복한다.
 * (3) Flyway 시드의 UTC_TIMESTAMP(6)이 동작해 score_setting 한 행이 DEFAULT 값으로 들어 있다.
 */
@PersistenceTest
class TierSchemaTest {

    @Autowired
    PlayerLaneTierRepository laneTiers;
    @Autowired
    ScoreSettingRepository scoreSettings;
    @Autowired
    PlayerRepository players;
    @Autowired
    EntityManager em;
    @Autowired
    JdbcTemplate jdbc;

    private Long newPlayerId() {
        return players.saveAndFlush(Player.unregistered("테스트닉")).id();
    }

    @Test
    void 시드된_점수표는_ScoreTable_DEFAULT와_같다() {
        ScoreSetting setting = scoreSettings.findById(ScoreSetting.SINGLE_ID).orElseThrow();

        assertThat(setting.toTable()).isEqualTo(ScoreTable.DEFAULT);
        assertThat(scoreSettings.count()).isEqualTo(1);
    }

    @Test
    void 시드의_UTC_TIMESTAMP는_현재_UTC_시각_근처다() {
        Instant updatedAt = scoreSettings.findById(ScoreSetting.SINGLE_ID).orElseThrow().updatedAt();

        assertThat(Duration.between(updatedAt, Instant.now()).abs()).isLessThan(Duration.ofHours(1));
    }

    @Test
    void 점수표_행을_잠가_읽을_수_있다() {
        assertThat(scoreSettings.findForUpdate()).isPresent();
    }

    @Test
    void 점수표를_덮어쓰고_다시_읽으면_같은_값이다() {
        ScoreTable custom = new ScoreTable(java.util.List.of(10, 20, 30, 40, 50, 60),
                java.util.Map.of(TierSub.HIGH, 5, TierSub.MID, 1, TierSub.LOW, -5),
                java.util.Map.of(Position.TOP, 1, Position.JUNGLE, 2, Position.MID, 3, Position.ADC, -4,
                        Position.SUPPORT, -999));
        ScoreSetting setting = scoreSettings.findById(ScoreSetting.SINGLE_ID).orElseThrow();
        setting.apply(custom);
        scoreSettings.saveAndFlush(setting);
        em.clear();

        assertThat(scoreSettings.findById(ScoreSetting.SINGLE_ID).orElseThrow().toTable()).isEqualTo(custom);
    }

    @Test
    void 라인_티어가_왕복하고_변경_컬럼은_changeTo가_채운다() {
        Long playerId = newPlayerId();
        PlayerLaneTier created = laneTiers.saveAndFlush(
                PlayerLaneTier.create(playerId, Position.MID, new LaneTier(3, TierSub.MID)));
        assertThat(created.lastChange()).isNull();

        created.changeTo(new LaneTier(2, TierSub.LOW));
        laneTiers.saveAndFlush(created);
        em.clear();

        PlayerLaneTier reloaded = laneTiers.findByPlayerId(playerId).get(0);
        assertThat(reloaded.laneTier()).isEqualTo(new LaneTier(2, TierSub.LOW));
        assertThat(reloaded.lastChange())
                .isEqualTo(new LastChange(LastChange.Direction.UP, new LaneTier(3, TierSub.MID)));
    }

    @Test
    void 같은_값으로_저장하면_마지막_변경이_바뀌지_않는다() {
        Long playerId = newPlayerId();
        PlayerLaneTier lane = laneTiers.saveAndFlush(
                PlayerLaneTier.create(playerId, Position.TOP, new LaneTier(4, TierSub.HIGH)));
        lane.changeTo(new LaneTier(5, TierSub.HIGH));
        LastChange before = lane.lastChange();

        lane.changeTo(new LaneTier(5, TierSub.HIGH));

        assertThat(before.direction()).isEqualTo(LastChange.Direction.DOWN);
        assertThat(lane.lastChange()).isEqualTo(before);
    }

    @Test
    void 선수를_지우면_라인_티어도_CASCADE로_지워진다() {
        Long playerId = newPlayerId();
        laneTiers.saveAndFlush(PlayerLaneTier.create(playerId, Position.ADC, new LaneTier(1, TierSub.HIGH)));

        jdbc.update("delete from player where id = ?", playerId);

        assertThat(jdbc.queryForObject("select count(*) from player_lane_tier where player_id = ?", Integer.class,
                playerId)).isZero();
    }

    @Test
    void 같은_선수_같은_라인_두_행은_UNIQUE가_막는다() {
        Long playerId = newPlayerId();
        laneTiers.saveAndFlush(PlayerLaneTier.create(playerId, Position.TOP, new LaneTier(1, TierSub.HIGH)));

        assertThatThrownBy(() -> laneTiers
                .saveAndFlush(PlayerLaneTier.create(playerId, Position.TOP, new LaneTier(2, TierSub.LOW))))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void 티어_범위와_변경_컬럼_all_or_none은_CHECK가_막는다() {
        Long playerId = newPlayerId();
        String insert = "insert into player_lane_tier (player_id, position, tier, sub, last_change_direction,"
                + " last_change_from_tier, last_change_from_sub, updated_at) values (?, ?, ?, ?, ?, ?, ?, UTC_TIMESTAMP(6))";

        assertThatThrownBy(() -> jdbc.update(insert, playerId, "TOP", 7, "MID", null, null, null))
                .as("티어 7").isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update(insert, playerId, "TOP", 3, "MID", "UP", null, null))
                .as("방향만 있음").isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update(insert, playerId, "TOP", 3, "MID", "SIDE", 3, "MID"))
                .as("방향 값 오류").isInstanceOf(DataAccessException.class);
        assertThat(jdbc.update(insert, playerId, "TOP", 3, "MID", "UP", 4, "LOW")).as("정상 행").isEqualTo(1);
    }

    @Test
    void 점수표_행은_id_1만_허용하고_범위_밖_값을_막는다() {
        String insert = "insert into score_setting values (?, 1,1,1,1,1,1, 0,0,0, 0,0,0,0,0, UTC_TIMESTAMP(6))";
        assertThatThrownBy(() -> jdbc.update(insert, 2)).isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbc.update("update score_setting set lane_adc = 1000 where id = 1"))
                .isInstanceOf(DataAccessException.class);
    }
}
