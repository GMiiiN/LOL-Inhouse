-- 티어 값이 실제로 바뀐 날(Asia/Seoul). 티어 조정 추천이 이 날 이후의 기록만 본다. NULL이면 전체 기록.
ALTER TABLE player_lane_tier ADD COLUMN tier_changed_on DATE NULL;
