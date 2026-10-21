-- 라인 티어·점수표. 설명: docs/design/m45-design.md §2, §10(Q2: 회차·변동 이력 테이블 없음), §11

-- 선수별 라인 티어(현재 값) + 마지막 변경(▲▼ 표시용). 라인 티어가 없으면 행이 없다.
-- 마지막 변경 3칸은 모두 NULL(변경 없음·새 라인)이거나 모두 값이다.
CREATE TABLE player_lane_tier (
  id                     BIGINT      NOT NULL AUTO_INCREMENT,
  player_id              BIGINT      NOT NULL,
  position               VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  tier                   INT         NOT NULL,                                       -- 1(최상)~6
  sub                    VARCHAR(4)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL, -- HIGH/MID/LOW = 상/중/하
  last_change_direction  VARCHAR(4)  CHARACTER SET ascii COLLATE ascii_bin NULL,     -- UP/DOWN
  last_change_from_tier  INT         NULL,
  last_change_from_sub   VARCHAR(4)  CHARACTER SET ascii COLLATE ascii_bin NULL,
  updated_at             DATETIME(6) NOT NULL,                                       -- UTC
  PRIMARY KEY (id),
  UNIQUE KEY uk_player_lane_tier_player_position (player_id, position),
  CONSTRAINT fk_player_lane_tier_player FOREIGN KEY (player_id) REFERENCES player (id) ON DELETE CASCADE,
  CONSTRAINT ck_player_lane_tier_position CHECK (position IN ('TOP','JUNGLE','MID','ADC','SUPPORT')),
  CONSTRAINT ck_player_lane_tier_tier CHECK (tier BETWEEN 1 AND 6),
  CONSTRAINT ck_player_lane_tier_sub CHECK (sub IN ('HIGH','MID','LOW')),
  CONSTRAINT ck_player_lane_tier_change_direction CHECK (last_change_direction IS NULL OR last_change_direction IN ('UP','DOWN')),
  CONSTRAINT ck_player_lane_tier_change_from_tier CHECK (last_change_from_tier IS NULL OR last_change_from_tier BETWEEN 1 AND 6),
  CONSTRAINT ck_player_lane_tier_change_from_sub CHECK (last_change_from_sub IS NULL OR last_change_from_sub IN ('HIGH','MID','LOW')),
  CONSTRAINT ck_player_lane_tier_change_all_or_none CHECK (
    (last_change_direction IS NULL AND last_change_from_tier IS NULL AND last_change_from_sub IS NULL)
    OR (last_change_direction IS NOT NULL AND last_change_from_tier IS NOT NULL AND last_change_from_sub IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 점수표(현재 값 1행). 기본값은 Java ScoreTable.DEFAULT와 같아야 한다(테스트로 묶음)
CREATE TABLE score_setting (
  id          INT         NOT NULL,
  base_tier1  INT         NOT NULL,
  base_tier2  INT         NOT NULL,
  base_tier3  INT         NOT NULL,
  base_tier4  INT         NOT NULL,
  base_tier5  INT         NOT NULL,
  base_tier6  INT         NOT NULL,
  sub_high    INT         NOT NULL,
  sub_mid     INT         NOT NULL,
  sub_low     INT         NOT NULL,
  lane_top    INT         NOT NULL,
  lane_jungle INT         NOT NULL,
  lane_mid    INT         NOT NULL,
  lane_adc    INT         NOT NULL,
  lane_support INT        NOT NULL,
  updated_at  DATETIME(6) NOT NULL,                                                  -- UTC
  PRIMARY KEY (id),
  CONSTRAINT ck_score_setting_single CHECK (id = 1),
  CONSTRAINT ck_score_setting_range CHECK (
    base_tier1 BETWEEN -999 AND 999 AND base_tier2 BETWEEN -999 AND 999 AND base_tier3 BETWEEN -999 AND 999
    AND base_tier4 BETWEEN -999 AND 999 AND base_tier5 BETWEEN -999 AND 999 AND base_tier6 BETWEEN -999 AND 999
    AND sub_high BETWEEN -999 AND 999 AND sub_mid BETWEEN -999 AND 999 AND sub_low BETWEEN -999 AND 999
    AND lane_top BETWEEN -999 AND 999 AND lane_jungle BETWEEN -999 AND 999 AND lane_mid BETWEEN -999 AND 999
    AND lane_adc BETWEEN -999 AND 999 AND lane_support BETWEEN -999 AND 999)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO score_setting (id, base_tier1, base_tier2, base_tier3, base_tier4, base_tier5, base_tier6,
                           sub_high, sub_mid, sub_low, lane_top, lane_jungle, lane_mid, lane_adc, lane_support, updated_at)
VALUES (1, 60, 52, 44, 36, 28, 20, 3, 0, -3, 0, 0, 0, -4, -3, UTC_TIMESTAMP(6));
