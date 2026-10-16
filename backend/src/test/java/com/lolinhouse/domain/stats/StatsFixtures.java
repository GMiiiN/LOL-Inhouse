package com.lolinhouse.domain.stats;

import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Position;

import java.util.ArrayList;
import java.util.List;

/** 테스트용 세트 기록 생성 도우미. 닉네임 없이 "p1" 같은 ID만 쓴다. */
final class StatsFixtures {

    private StatsFixtures() {
    }

    static PlayerSetRecord record(String playerId, String duelId, String setId, Position position,
                                  String champion, boolean win, int kills, int deaths, int assists) {
        return recordForTeam(playerId, duelId, setId, DuelTeam.TEAM1, position, champion, win, kills, deaths, assists);
    }

    static PlayerSetRecord recordForTeam(String playerId, String duelId, String setId, DuelTeam team,
                                         Position position, String champion, boolean win,
                                         int kills, int deaths, int assists) {
        return new PlayerSetRecord(playerId, duelId, setId, team, position, champion, win,
                kills, deaths, assists, 200, 12000);
    }

    /** 같은 K/D/A를 가진 세트를 count개 만든다. 앞의 wins개가 승리. setId는 "ID-포지션-번호". */
    static List<PlayerSetRecord> games(String playerId, Position position, int count, int wins,
                                       int kills, int deaths, int assists) {
        List<PlayerSetRecord> records = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            records.add(record(playerId, "d-" + playerId, playerId + "-" + position + "-" + i, position,
                    "Ahri", i < wins, kills, deaths, assists));
        }
        return records;
    }

    @SafeVarargs
    static List<PlayerSetRecord> concat(List<PlayerSetRecord>... groups) {
        List<PlayerSetRecord> all = new ArrayList<>();
        for (List<PlayerSetRecord> group : groups) {
            all.addAll(group);
        }
        return all;
    }
}
