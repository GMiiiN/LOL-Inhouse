package com.lolinhouse.registration;

import java.util.List;

import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.Side;
import com.lolinhouse.domain.player.MatchType;
import com.lolinhouse.ingest.extract.ExtractedScoreboard.TopResult;

/**
 * 역할: GET draft 응답. 추출 사실(화면 순서)에 닉네임 매칭·챔피언 후보를 붙인 것과, 그 기본값으로 채운 저장 명령(defaults)·평가(evaluation).
 * 형태는 프런트 registrationTypes.ts의 DraftResponse와 같다. 설명: docs/design/m4-design.md §5
 */
public record DraftResponse(Long jobId, Long duelId, Side topSide, Position capturerPosition, TopResult topResult,
        String shotDate, String duration, Team top, Team bottom, SetRegistrationRequest defaults,
        Evaluation evaluation) {

    public record Team(int displayedKills, int displayedDeaths, int displayedAssists, int displayedGold,
            List<Row> rows, List<Ban> bans) {
    }

    public record Row(int screenRow, Position position, String readNickname, boolean truncated, Match match,
            int kills, int deaths, int assists, int cs, int gold, List<Integer> portraitBox, ChampionGuess champion) {
    }

    public record Ban(int slot, List<Integer> box, ChampionGuess champion) {
    }

    public record Match(MatchType type, Long playerId, String playerDisplayName, double similarity,
            List<Candidate> candidates, boolean fullNicknameRequired, String newPlayerName) {
    }

    public record Candidate(Long playerId, String displayName, String nickname, double similarity) {
    }

    public record ChampionGuess(List<ChampionCandidate> candidates, double margin, boolean uncertain,
            boolean cropFailed) {
    }

    public record ChampionCandidate(String id, double score) {
    }
}
