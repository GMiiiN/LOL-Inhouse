-- 경기 시간을 모르는 기록(결과창 없이 가져온 기록)을 허용한다. ADR 014. 지문은 시간 자리를 '?'로 둔다.
ALTER TABLE game_set DROP CHECK ck_game_set_duration;
ALTER TABLE game_set MODIFY duration_sec INT NULL;
ALTER TABLE game_set ADD CONSTRAINT ck_game_set_duration CHECK (duration_sec IS NULL OR duration_sec > 0);
