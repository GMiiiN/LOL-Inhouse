package com.lolinhouse.record;

import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Position;

/** 역할: 한 세트에서 한 선수의 기록 프로젝션(screen_row는 공개하지 않아 읽지 않는다). */
public record PlayerRow(
        long setId,
        long playerId,
        DuelTeam team,
        Position position,
        String championKey,
        int kills,
        int deaths,
        int assists,
        int cs,
        int gold) {
}
