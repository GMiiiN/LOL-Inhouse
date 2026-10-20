-- 결과창 추출 작업(M4)과 대결의 피어리스 방식. 설명: docs/design/m4-design.md §1·§2·§11.1, ADR 009
-- 추출 작업에는 추출 사실만 저장한다. 선수 매칭·1팀/2팀 판별 같은 초안은 조회할 때마다 계산한다.
-- 적용된 V2는 수정하지 않고 여기서 추가한다.

CREATE TABLE extraction_job (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  duel_id           BIGINT       NOT NULL,
  status            VARCHAR(10)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  top_side          VARCHAR(4)   CHARACTER SET ascii COLLATE ascii_bin NOT NULL,  -- 업로드 전 선택값(확인 화면에서 바뀔 수 있음 → 저장 명령이 최종)
  capturer_position VARCHAR(10)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  image_hash        VARCHAR(64)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL,  -- SHA-256 hex(업로드 바이트)
  image_key         VARCHAR(255) NOT NULL,                                        -- ImageStore 키
  result_json       JSON         NULL,                                            -- ExtractionResult v1. SUCCEEDED일 때만
  error_code        VARCHAR(40)  CHARACTER SET ascii COLLATE ascii_bin NULL,      -- FAILED일 때만(EXPIRED·SUPERSEDED 포함). 원문 메시지는 저장하지 않음
  attempt_count     INT          NOT NULL,
  game_set_id       BIGINT       NULL,                                            -- 저장된 세트(1:1). NULL = 아직 저장 안 됨
  created_at        DATETIME(6)  NOT NULL,
  started_at        DATETIME(6)  NULL,
  finished_at       DATETIME(6)  NULL,
  updated_at        DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_extraction_job_game_set (game_set_id),
  KEY ix_extraction_job_duel_id (duel_id),
  KEY ix_extraction_job_image_hash (image_hash),
  CONSTRAINT fk_extraction_job_duel     FOREIGN KEY (duel_id)     REFERENCES duel (id)     ON DELETE RESTRICT,
  CONSTRAINT fk_extraction_job_game_set FOREIGN KEY (game_set_id) REFERENCES game_set (id) ON DELETE RESTRICT,
  CONSTRAINT ck_extraction_job_status   CHECK (status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED')),
  CONSTRAINT ck_extraction_job_top_side CHECK (top_side IN ('BLUE','RED')),
  CONSTRAINT ck_extraction_job_capturer_position CHECK (capturer_position IN ('TOP','JUNGLE','MID','ADC','SUPPORT')),
  CONSTRAINT ck_extraction_job_error    CHECK ((status = 'FAILED') = (error_code IS NOT NULL)),
  CONSTRAINT ck_extraction_job_result   CHECK ((status = 'SUCCEEDED') = (result_json IS NOT NULL)),
  CONSTRAINT ck_extraction_job_saved    CHECK (game_set_id IS NULL OR status = 'SUCCEEDED'),
  CONSTRAINT ck_extraction_job_attempts CHECK (attempt_count BETWEEN 0 AND 10)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 대결의 피어리스 방식. RESET = 제한 없이 시작, CONTINUE = 직전 초기화 이후의 픽을 이어서 제한.
-- DEFAULT는 이미 있는 행을 채우기 위한 것이고, 앱은 항상 값을 명시해 저장한다.
ALTER TABLE duel
  ADD COLUMN fearless_mode VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'RESET',
  ADD CONSTRAINT ck_duel_fearless_mode CHECK (fearless_mode IN ('RESET','CONTINUE'));
