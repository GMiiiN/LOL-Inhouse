package com.lolinhouse.record;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.Side;
import com.lolinhouse.player.Player;
import com.lolinhouse.player.PlayerRepository;
import com.lolinhouse.session.Duel;
import com.lolinhouse.session.DuelRepository;
import com.lolinhouse.session.FearlessMode;
import com.lolinhouse.session.GameSet;
import com.lolinhouse.session.GameSet.DisplayedTotals;
import com.lolinhouse.session.GameSetRepository;
import com.lolinhouse.session.InhouseSession;
import com.lolinhouse.session.InhouseSessionRepository;

/**
 * 역할: M5 공개 조회 통합 테스트용 데이터 준비(리포지토리로 직접 저장). 작업자 B~F가 같이 쓰는 공통 픽스처다.
 * 테스트 DB는 서로 공유되므로 날짜는 2100년대 한 구간을 쓰고, 조회 테스트는 from/to로 자기 구간만 읽는다.
 * 챔피언 키는 "C00" 같은 가짜 키다(카탈로그 검사를 거치지 않는 저장이라 괜찮다).
 */
public final class PublicReadFixtures {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private final PlayerRepository players;
    private final InhouseSessionRepository sessions;
    private final DuelRepository duels;
    private final GameSetRepository gameSets;

    public PublicReadFixtures(PlayerRepository players, InhouseSessionRepository sessions, DuelRepository duels,
            GameSetRepository gameSets) {
        this.players = players;
        this.sessions = sessions;
        this.duels = duels;
        this.gameSets = gameSets;
    }

    /** 이 테스트 클래스가 쓰는 날짜 구간의 n번째 날(구간을 나누려면 base 해가 다른 값을 넘긴다). */
    public static LocalDate day(int baseYear, int n) {
        return LocalDate.of(baseYear, 1, 1).plusDays(n);
    }

    /** 실명이 있는 등록 선수. */
    public long registeredPlayer(String realName) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return players.save(Player.registered("nick" + suffix, realName + suffix, Position.MID)).id();
    }

    /** 실명이 없는 미등록 선수(표시 이름 = 닉네임). */
    public long unregisteredPlayer(String nickname) {
        return players.save(Player.unregistered(nickname + UUID.randomUUID().toString().substring(0, 8))).id();
    }

    /** 선수 10명(앞 5명이 TEAM1, 뒤 5명이 TEAM2에 해당하는 목록). */
    public List<Long> tenPlayers() {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ids.add(registeredPlayer("선수" + i));
        }
        return ids;
    }

    public long session(LocalDate startDate) {
        return sessions.save(InhouseSession.of(startDate)).id();
    }

    public long duel(long sessionId, int seq, FearlessMode mode) {
        return duels.save(Duel.of(sessionId, seq, mode)).id();
    }

    /**
     * 세트 하나를 저장한다. 선수 10명은 team1 → team2 순서, 각 팀 안에서는 TOP→SUPPORT 순서다.
     * team1Champions·team2Champions는 팀 안 라인 순서로 5개씩, bans는 B1 R1 ... B5 R5 10개다.
     * 지문이 겹치지 않도록 경기 시간을 호출마다 다르게 둔다.
     */
    public GameSet set(long duelId, int setNo, Side team1Side, DuelTeam winner, List<Long> playerIds,
            List<String> team1Champions, List<String> team2Champions, List<String> bans) {
        int unique = SEQUENCE.incrementAndGet();
        GameSet set = GameSet.create(duelId, setNo, team1Side, winner, DuelTeam.TEAM1, day(2090, unique % 3000),
                600 + unique, new DisplayedTotals(20, 15, 40, 60000 + unique), new DisplayedTotals(15, 20, 30, 55000),
                null, null);
        Position[] positions = Position.values();
        for (int i = 0; i < 5; i++) {
            set.addPlayer(playerIds.get(i), DuelTeam.TEAM1, positions[i], i + 1, team1Champions.get(i), 4, 2, 6,
                    200 + i, 10000 + i);
            set.addPlayer(playerIds.get(5 + i), DuelTeam.TEAM2, positions[i], i + 1, team2Champions.get(i), 2, 4, 3,
                    180 + i, 9000 + i);
        }
        for (int turn = 1; turn <= 10; turn++) {
            set.addBan(turn, turn % 2 == 1 ? Side.BLUE : Side.RED, bans.get(turn - 1));
        }
        return gameSets.saveAndFlush(set);
    }

    /** "C" + 두 자리 번호 → 가짜 챔피언 키. */
    public static String champ(int number) {
        return String.format("C%02d", number);
    }

    /** from부터 count개의 연속 챔피언 키. */
    public static List<String> champs(int from, int count) {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            keys.add(champ(from + i));
        }
        return keys;
    }
}
