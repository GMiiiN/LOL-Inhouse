-- 요주 인물 표시(계산에는 영향 없음, 설계 §13.2)
ALTER TABLE player ADD COLUMN flagged BOOLEAN NOT NULL DEFAULT FALSE;
