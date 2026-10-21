package com.lolinhouse.record;

import java.time.LocalDate;
import java.util.List;

import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.Side;

/**
 * 역할: 공개 API의 세트 한 판(기록 목록 펼침, 세트 상세, 프로필 최근 경기, 홈이 모두 같은 모양). SetViewAssembler가 만든다.
 * 공개하지 않는 값: 이미지 키·해시, 지문, top_team, screen_row, 생성·수정·삭제 시각. 날짜는 LocalDate만 쓴다(Instant 없음).
 * teams는 [TEAM1, TEAM2] 순서 고정, bans는 turn 1~10 순서, fearlessRestricted는 이 세트 시점의 피어리스 제한 챔피언 키(오름차순).
 */
public record SetView(
        long setId,
        int setNo,
        long duelId,
        int duelSeq,
        long sessionId,
        LocalDate sessionDate,
        LocalDate shotDate,
        Integer durationSec,
        Side team1Side,
        DuelTeam winnerTeam,
        List<TeamView> teams,
        List<BanView> bans,
        List<String> fearlessRestricted) {

    /** 한 팀. displayed는 결과창에 표시된 합계(선수 행 합계와 다를 수 있다). players는 TOP→SUPPORT. */
    public record TeamView(DuelTeam team, Side side, boolean won, Displayed displayed, List<PlayerLine> players) {
    }

    public record Displayed(int kills, int deaths, int assists, int gold) {
    }

    /** 선수 한 줄. 이름은 displayName(등록 선수는 실명, 미등록은 게임 닉네임)뿐이고 별칭은 없다. */
    public record PlayerLine(
            long playerId,
            String displayName,
            boolean registered,
            Position position,
            String championKey,
            int kills,
            int deaths,
            int assists,
            int cs,
            int gold) {
    }

    public record BanView(int turn, Side side, String championKey) {
    }
}
