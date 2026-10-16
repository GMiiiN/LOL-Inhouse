package com.lolinhouse.domain.player;

import com.lolinhouse.domain.player.NicknameMatchResult.Candidate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 역할: AI가 읽은 닉네임을 알려진 별칭들과 비교해 EXACT / TRUNCATED_AUTO / CANDIDATES / NEW_UNREGISTERED로 분류한다.
 * 규칙: 비교는 대소문자를 구분하지 않는다(NicknameNormalizer.comparisonKey). 정확히 한 선수와 일치할 때만 EXACT,
 * 잘린 닉네임(끝이 "…")은 앞부분 유사도 0.70 이상인 선수가 정확히 한 명일 때만 자동 연결한다.
 * 그 밖의 비슷한 닉네임(전체 닉네임의 오인식 포함)은 자동 연결하지 않고 후보로 보여준다.
 * 설명: docs/study/07-domain-player-stats.md
 */
public class NicknameMatcher {

    static final double AUTO_LINK_THRESHOLD = 0.70;
    static final double CANDIDATE_THRESHOLD = 0.5;
    static final int MAX_CANDIDATES = 3;
    private static final String ELLIPSIS = "…";

    public NicknameMatchResult match(String extractedNickname, List<KnownAlias> aliases) {
        // 주의: NFKC는 "…"(U+2026)를 "..."로 바꾸므로 잘림 여부는 정규화 전에 본다.
        // 끝의 NBSP·U+202F 같은 공백이 "…"를 가리지 않도록 유니코드 공백을 먼저 걷어낸다.
        String trimmed = NicknameNormalizer.stripUnicodeSpaces(extractedNickname);
        boolean truncated = trimmed.endsWith(ELLIPSIS);
        String visibleRaw = truncated ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        String displayName = NicknameNormalizer.normalize(visibleRaw);
        String visibleKey = NicknameNormalizer.comparisonKey(visibleRaw);
        if (visibleKey.isEmpty()) {
            throw new IllegalArgumentException("닉네임이 비어 있습니다.");
        }

        if (!truncated) {
            List<Candidate> exactMatches = exactMatchesByPlayer(visibleKey, aliases);
            if (exactMatches.size() == 1) {
                return NicknameMatchResult.exact(exactMatches.get(0).playerId());
            }
            if (exactMatches.size() > 1) {
                // 서로 다른 선수의 별칭이 같은 키로 정규화됨: 임의로 고르지 않고 담당자가 고른다.
                return NicknameMatchResult.candidates(exactMatches, false);
            }
        }

        List<Candidate> ranked = rankByPlayer(visibleKey, truncated, aliases);

        if (truncated && isUniqueAutoLink(ranked)) {
            return NicknameMatchResult.truncatedAuto(ranked.get(0).playerId(), ranked.get(0).similarity());
        }

        List<Candidate> candidates = ranked.stream()
                .filter(candidate -> candidate.similarity() >= CANDIDATE_THRESHOLD)
                .limit(MAX_CANDIDATES)
                .toList();
        if (!candidates.isEmpty()) {
            return NicknameMatchResult.candidates(candidates, truncated);
        }
        return truncated
                ? NicknameMatchResult.newUnregisteredNeedingFullNickname()
                : NicknameMatchResult.newUnregistered(displayName);
    }

    /** 키가 같은 별칭을 가진 선수들(선수당 한 번, 입력 순서). 같은 선수의 별칭 둘이 같아도 한 명으로 센다. */
    private List<Candidate> exactMatchesByPlayer(String visibleKey, List<KnownAlias> aliases) {
        Map<String, Candidate> byPlayer = new LinkedHashMap<>();
        for (KnownAlias alias : aliases) {
            if (NicknameNormalizer.comparisonKey(alias.nickname()).equals(visibleKey)) {
                byPlayer.putIfAbsent(alias.playerId(), new Candidate(alias.playerId(), alias.nickname(), 1.0));
            }
        }
        return new ArrayList<>(byPlayer.values());
    }

    /** 선수당 가장 비슷한 별칭 하나만 남기고 유사도 내림차순(동률은 입력 순서)으로 정렬한다. */
    private List<Candidate> rankByPlayer(String visibleKey, boolean truncated, List<KnownAlias> aliases) {
        int visibleLength = visibleKey.codePointCount(0, visibleKey.length());
        Map<String, Candidate> bestByPlayer = new LinkedHashMap<>();
        for (KnownAlias alias : aliases) {
            String aliasKey = NicknameNormalizer.comparisonKey(alias.nickname());
            // 잘린 입력은 별칭도 같은 길이의 앞부분만 비교한다.
            String comparable = truncated ? prefix(aliasKey, visibleLength) : aliasKey;
            double similarity = Levenshtein.similarity(visibleKey, comparable);
            bestByPlayer.merge(alias.playerId(), new Candidate(alias.playerId(), alias.nickname(), similarity),
                    (old, added) -> added.similarity() > old.similarity() ? added : old);
        }
        List<Candidate> ranked = new ArrayList<>(bestByPlayer.values());
        ranked.sort(Comparator.comparingDouble(Candidate::similarity).reversed());
        return ranked;
    }

    /** 0.70 이상인 선수가 정확히 한 명일 때만 자동 연결(2등이 낮아도 0.70 이상이면 사람이 고른다). */
    private boolean isUniqueAutoLink(List<Candidate> ranked) {
        long aboveThreshold = ranked.stream()
                .filter(candidate -> candidate.similarity() >= AUTO_LINK_THRESHOLD)
                .count();
        return aboveThreshold == 1;
    }

    private String prefix(String text, int codePointCount) {
        int length = text.codePointCount(0, text.length());
        return text.substring(0, text.offsetByCodePoints(0, Math.min(codePointCount, length)));
    }
}
