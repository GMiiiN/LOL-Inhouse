package com.lolinhouse.domain.player;

import java.util.List;

/**
 * 역할: 닉네임 매칭 결과. type에 따라 쓰는 필드가 다르다.
 * EXACT/TRUNCATED_AUTO: playerId, similarity. ONE_CHAR_AUTO: playerId, similarity, distance(편집 거리, 항상 1).
 * distance는 ONE_CHAR_AUTO에서만 의미가 있고 다른 type은 0. CANDIDATES: candidates.
 * NEW_UNREGISTERED: newPlayerName(표시 이름). 입력이 잘린 닉네임이면 어떤 type이든
 * fullNicknameRequired=true로, 담당자가 결과를 거부하고 새 선수를 만들 때 전체 닉네임 입력이 필요하다.
 */
public record NicknameMatchResult(
        MatchType type,
        String playerId,
        double similarity,
        int distance,
        List<Candidate> candidates,
        boolean fullNicknameRequired,
        String newPlayerName) {

    public NicknameMatchResult {
        // 호출자가 나중에 리스트를 바꿔도 결과가 변하지 않도록 불변 복사본을 쓴다.
        candidates = List.copyOf(candidates);
    }

    /** 후보 한 명: 선수의 별칭 중 가장 비슷한 것과 그 유사도. */
    public record Candidate(String playerId, String nickname, double similarity) {
    }

    static NicknameMatchResult exact(String playerId) {
        return new NicknameMatchResult(MatchType.EXACT, playerId, 1.0, 0, List.of(), false, null);
    }

    static NicknameMatchResult truncatedAuto(String playerId, double similarity) {
        return new NicknameMatchResult(MatchType.TRUNCATED_AUTO, playerId, similarity, 0, List.of(), true, null);
    }

    static NicknameMatchResult oneCharAuto(String playerId, double similarity, int distance) {
        return new NicknameMatchResult(MatchType.ONE_CHAR_AUTO, playerId, similarity, distance, List.of(), false, null);
    }

    static NicknameMatchResult candidates(List<Candidate> candidates, boolean truncated) {
        return new NicknameMatchResult(MatchType.CANDIDATES, null, 0.0, 0, candidates, truncated, null);
    }

    static NicknameMatchResult newUnregistered(String displayName) {
        return new NicknameMatchResult(MatchType.NEW_UNREGISTERED, null, 0.0, 0, List.of(), false, displayName);
    }

    /** 잘린 닉네임은 표시 이름으로 쓸 수 없으니 전체 닉네임 입력을 요구한다. */
    static NicknameMatchResult newUnregisteredNeedingFullNickname() {
        return new NicknameMatchResult(MatchType.NEW_UNREGISTERED, null, 0.0, 0, List.of(), true, null);
    }
}
