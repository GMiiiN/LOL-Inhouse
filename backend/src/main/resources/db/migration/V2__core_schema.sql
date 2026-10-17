-- 핵심 스키마. 설명: ADR 005·006

CREATE TABLE admin_account (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  username      VARCHAR(50)  NOT NULL,
  password_hash VARCHAR(100) NOT NULL,                 -- BCrypt(60자) + 여유
  created_at    DATETIME(6)  NOT NULL,                 -- UTC
  PRIMARY KEY (id),
  UNIQUE KEY uk_admin_account_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE player (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  game_nickname VARCHAR(100) NOT NULL,                 -- 대표 닉네임(normalize 표기). 미등록이면 표시 이름
  real_name     VARCHAR(50)  NULL,                     -- NULL = 미등록
  main_position VARCHAR(10)  CHARACTER SET ascii COLLATE ascii_bin NULL,
  created_at    DATETIME(6)  NOT NULL,
  updated_at    DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT ck_player_main_position CHECK (main_position IN ('TOP','JUNGLE','MID','ADC','SUPPORT')),
  CONSTRAINT ck_player_real_name CHECK (real_name IS NULL OR CHAR_LENGTH(TRIM(real_name)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE player_alias (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  player_id    BIGINT       NOT NULL,
  nickname     VARCHAR(100) NOT NULL,                  -- NicknameNormalizer.normalize (대소문자 유지, 화면용)
  nickname_key VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL, -- comparisonKey. 바이너리 비교 = Java equals
  created_at   DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_player_alias_nickname_key (nickname_key),
  KEY ix_player_alias_player_id (player_id),
  CONSTRAINT fk_player_alias_player FOREIGN KEY (player_id) REFERENCES player (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE inhouse_session (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  start_date DATE        NOT NULL,                     -- 내전 시작일(한국 날짜, 변환 없음)
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_inhouse_session_start_date (start_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE duel (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  session_id BIGINT      NOT NULL,
  seq        INT         NOT NULL,                     -- 묶음 안 "대결 N"
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_duel_session_seq (session_id, seq),
  CONSTRAINT fk_duel_session FOREIGN KEY (session_id) REFERENCES inhouse_session (id) ON DELETE RESTRICT,
  CONSTRAINT ck_duel_seq CHECK (seq >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE game_set (
  id                      BIGINT       NOT NULL AUTO_INCREMENT,
  duel_id                 BIGINT       NOT NULL,
  set_no                  INT          NOT NULL,
  team1_side              VARCHAR(4)   CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  winner_team             VARCHAR(5)   CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  top_team                VARCHAR(5)   CHARACTER SET ascii COLLATE ascii_bin NULL, -- 결과창 위쪽(캡처한 사람) 팀. 수동 입력이면 NULL
  shot_date               DATE         NOT NULL,
  duration_sec            INT          NOT NULL,
  team1_displayed_kills   INT NOT NULL, team1_displayed_deaths INT NOT NULL,
  team1_displayed_assists INT NOT NULL, team1_displayed_gold   INT NOT NULL,
  team2_displayed_kills   INT NOT NULL, team2_displayed_deaths INT NOT NULL,
  team2_displayed_assists INT NOT NULL, team2_displayed_gold   INT NOT NULL,
  fingerprint             VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, -- SetFingerprint.canonical
  image_hash              VARCHAR(64)  CHARACTER SET ascii COLLATE ascii_bin NULL,     -- SHA-256 hex
  image_key               VARCHAR(255) NULL,                                           -- S3 키(M4)
  deleted_at              DATETIME(6)  NULL,                                           -- 소프트 삭제(M6)
  created_at              DATETIME(6)  NOT NULL,
  updated_at              DATETIME(6)  NOT NULL,
  active_fingerprint      VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin
                          GENERATED ALWAYS AS (IF(deleted_at IS NULL, fingerprint, NULL)) STORED,
  active_set_no           INT GENERATED ALWAYS AS (IF(deleted_at IS NULL, set_no, NULL)) STORED,
  PRIMARY KEY (id),
  UNIQUE KEY uk_game_set_active_fingerprint (active_fingerprint),
  UNIQUE KEY uk_game_set_duel_active_set_no (duel_id, active_set_no),
  KEY ix_game_set_image_hash (image_hash),
  CONSTRAINT fk_game_set_duel FOREIGN KEY (duel_id) REFERENCES duel (id) ON DELETE RESTRICT,
  CONSTRAINT ck_game_set_set_no      CHECK (set_no >= 1),
  CONSTRAINT ck_game_set_team1_side  CHECK (team1_side IN ('BLUE','RED')),
  CONSTRAINT ck_game_set_winner_team CHECK (winner_team IN ('TEAM1','TEAM2')),
  CONSTRAINT ck_game_set_top_team    CHECK (top_team IN ('TEAM1','TEAM2')),
  CONSTRAINT ck_game_set_duration    CHECK (duration_sec > 0),
  CONSTRAINT ck_game_set_totals CHECK (team1_displayed_kills >= 0 AND team1_displayed_deaths >= 0
      AND team1_displayed_assists >= 0 AND team1_displayed_gold >= 0 AND team2_displayed_kills >= 0
      AND team2_displayed_deaths >= 0 AND team2_displayed_assists >= 0 AND team2_displayed_gold >= 0),
  CONSTRAINT ck_game_set_image CHECK ((image_hash IS NULL) = (image_key IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE game_set_player (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  game_set_id  BIGINT      NOT NULL,
  player_id    BIGINT      NOT NULL,
  team         VARCHAR(5)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  position     VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  screen_row   INT         NULL,
  champion_key VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  kills INT NOT NULL, deaths INT NOT NULL, assists INT NOT NULL, cs INT NOT NULL, gold INT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_game_set_player_player     (game_set_id, player_id),
  UNIQUE KEY uk_game_set_player_position   (game_set_id, team, position),
  UNIQUE KEY uk_game_set_player_screen_row (game_set_id, team, screen_row),
  KEY ix_game_set_player_player_id (player_id),
  CONSTRAINT fk_game_set_player_game_set FOREIGN KEY (game_set_id) REFERENCES game_set (id) ON DELETE CASCADE,
  CONSTRAINT fk_game_set_player_player   FOREIGN KEY (player_id)   REFERENCES player (id)   ON DELETE RESTRICT,
  CONSTRAINT ck_game_set_player_team       CHECK (team IN ('TEAM1','TEAM2')),
  CONSTRAINT ck_game_set_player_position   CHECK (position IN ('TOP','JUNGLE','MID','ADC','SUPPORT')),
  CONSTRAINT ck_game_set_player_screen_row CHECK (screen_row BETWEEN 1 AND 5),
  CONSTRAINT ck_game_set_player_stats CHECK (kills >= 0 AND deaths >= 0 AND assists >= 0 AND cs >= 0 AND gold >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE game_set_ban (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  game_set_id  BIGINT      NOT NULL,
  turn         INT         NOT NULL,                 -- 1~10 = B1 R1 B2 R2 B3 R3 B4 R4 B5 R5
  side         VARCHAR(4)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  champion_key VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_game_set_ban_turn (game_set_id, turn),
  CONSTRAINT fk_game_set_ban_game_set FOREIGN KEY (game_set_id) REFERENCES game_set (id) ON DELETE CASCADE,
  CONSTRAINT ck_game_set_ban_turn CHECK (turn BETWEEN 1 AND 10),
  CONSTRAINT ck_game_set_ban_side CHECK ((turn % 2 = 1 AND side = 'BLUE') OR (turn % 2 = 0 AND side = 'RED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- team_swap, audit_log, extraction_job은 필드가 확정되는 M4/M6에서 새 마이그레이션으로 추가한다. 적용된 V2는 수정하지 않는다.
