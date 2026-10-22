package com.lolinhouse.registration;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.lolinhouse.champion.CatalogSnapshot;
import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.Side;
import com.lolinhouse.domain.player.NicknameNormalizer;
import com.lolinhouse.domain.set.DuelTeamRules;
import com.lolinhouse.domain.set.FearlessChain.ChainDuel;
import com.lolinhouse.domain.set.FearlessConflicts;
import com.lolinhouse.domain.set.FearlessConflicts.Conflict;
import com.lolinhouse.domain.set.DuelTeamRules.TeamDetection;
import com.lolinhouse.domain.set.SetDraft;
import com.lolinhouse.domain.set.SetDraft.DraftPlayer;
import com.lolinhouse.domain.set.SetDraft.DraftTeam;
import com.lolinhouse.domain.set.SetDraftValidator;
import com.lolinhouse.domain.set.SetDraftValidator.ValidationContext;
import com.lolinhouse.domain.set.SetDraftValidator.ValidationResult;
import com.lolinhouse.domain.set.SetFingerprint;
import com.lolinhouse.domain.set.SetFingerprint.TeamTotals;
import com.lolinhouse.domain.set.TeamChangeDetector;
import com.lolinhouse.domain.set.TeamChangeDetector.Kind;
import com.lolinhouse.domain.set.TeamChangeDetector.TeamChange;
import com.lolinhouse.player.PlayerAlias;
import com.lolinhouse.registration.Evaluation.Detection;
import org.springframework.stereotype.Component;

/**
 * 역할: 저장 명령 하나를 현재 대결 상태·선수 목록·챔피언 카탈로그로 평가한다(초안 첫 평가, check, 저장 직전 검사가 같은 코드를 쓴다).
 * 순서: 날짜·시간 파싱 → 선수 확정 규칙 → 챔피언 id → A팀/B팀 판별 → 팀 변경 감지 → 도메인 검증(SetDraftValidator) → 지문·중복.
 * 예외를 던지는 도메인 함수(SetFingerprint, BanOrder)는 입력이 유효할 때만 부르고, 평가용 밴은 비어 있어도(null) 되는 배열로 따로 만든다.
 * DB를 읽지 않는다(DuelState·PlayerDirectory·DuplicateLookup으로 받는다). 설명: docs/design/m4-design.md §3, §11.1, §11.2
 */
@Component
public class SetEvaluator {

    private static final String ELLIPSIS = "…";

    public Evaluation evaluate(SetRegistrationRequest request, DuelState duel, PlayerDirectory players,
            CatalogSnapshot catalog, DuplicateLookup duplicates) {
        return evaluate(request, duel, players, catalog, duplicates, false, null, null).evaluation();
    }

    /** 결과창 없이 가져오는 세트: 경기 시간은 request.duration이 아니라 인자로 받고, null이면 "모름"으로 지문을 만든다. */
    public Evaluation evaluateImport(SetRegistrationRequest request, Integer durationSec, DuelState duel,
            PlayerDirectory players, CatalogSnapshot catalog, DuplicateLookup duplicates) {
        return evaluate(request, duel, players, catalog, duplicates, true, durationSec, null).evaluation();
    }

    /**
     * 저장된 세트 수정용 평가. 세트 번호는 저장 번호, 위쪽 팀은 수정 전 A팀과의 겹침으로 판별하고(팀 변경 감지는 항상 NONE),
     * 피어리스는 앞쪽 겹침이 오류·뒤쪽 겹침이 경고(+fearlessConflicts)다. 경기 시간이 빈 문자열이면 "모름"이다.
     * duplicates는 자기 자신을 뺀 조회여야 한다.
     */
    public EditResult evaluateEdit(SetRegistrationRequest request, EditContext edit, PlayerDirectory players,
            CatalogSnapshot catalog, DuplicateLookup duplicates) {
        Set<String> front = new HashSet<>(FearlessConflicts.restrictedAt(edit.sessionDuels(), edit.duelId(),
                edit.setNo(), edit.otherPicks()));
        DuelState state = new DuelState(edit.duelId(), edit.setNo(), null, front);
        return evaluate(request, state, players, catalog, duplicates, false, null, edit);
    }

    private EditResult evaluate(SetRegistrationRequest request, DuelState duel, PlayerDirectory players,
            CatalogSnapshot catalog, DuplicateLookup duplicates, boolean imported, Integer importedDuration,
            EditContext edit) {
        List<String> errors = new ArrayList<>();
        int setNo = duel.nextSetNo();

        LocalDate shotDate = parseDate(request.shotDate(), errors);
        boolean durationUnknown = edit != null && (request.duration() == null || request.duration().isBlank());
        Integer durationSec = imported ? importedDuration : durationUnknown ? null
                : parseDuration(request.duration(), errors);

        TeamRows top = resolveRows("top", request.top(), players, errors);
        TeamRows bottom = resolveRows("bottom", request.bottom(), players, errors);
        checkNewNicknames(request, players, errors);
        checkChampionIds(request, catalog, errors);

        Judgement judgement = edit != null ? judgeEdit(edit, top, errors)
                : judge(request, duel, top, bottom, setNo, errors);

        ValidationResult validation = SetDraftValidator.validate(toDraft(request, top, bottom),
                new ValidationContext(setNo, judgement.uncertain() ? null : judgement.topTeam(),
                        duel.restrictedChampions()));
        errors.addAll(validation.errors());

        String fingerprint = null;
        DuplicateSet duplicate = null;
        if (shotDate != null && (durationSec != null || imported || durationUnknown)) {
            fingerprint = fingerprint(request, shotDate, durationSec, errors);
            if (fingerprint != null) {
                duplicate = duplicates.find(fingerprint).orElse(null);
            }
        }

        Side team1Side = judgement.topTeam() == null ? null
                : judgement.topTeam() == DuelTeam.TEAM1 ? request.topSide() : request.topSide().opposite();
        List<String> warnings = validation.warnings();
        List<Conflict> rearConflicts = List.of();
        if (edit != null) {
            warnings = new ArrayList<>(warnings);
            adjacentTeamWarning(edit, judgement, top, bottom).ifPresent(warnings::add);
            rearConflicts = rearConflicts(request, edit);
            warnings.addAll(conflictWarnings(rearConflicts));
        }
        Evaluation evaluation = new Evaluation(setNo, judgement.topTeam(),
                new Detection(judgement.method(), judgement.overlap(), judgement.uncertain()), team1Side, fingerprint,
                duplicate, List.copyOf(errors), List.copyOf(warnings), judgement.teamChange());
        return new EditResult(evaluation, rearConflicts);
    }

    /** 위쪽 팀 판별 + 팀 변경 감지 결과. */
    private record Judgement(DuelTeam topTeam, DetectionMethod method, Integer overlap, boolean uncertain,
            TeamChange teamChange) {
    }

    /** 한 팀의 행을 선수 키로 바꾼 것. pending이면 아직 확정 안 된 행이 있다. */
    private record TeamRows(List<String> keys, List<Position> positions, boolean pending) {

        Map<String, Position> byPlayer() {
            Map<String, Position> map = new LinkedHashMap<>();
            for (int i = 0; i < keys.size(); i++) {
                map.put(keys.get(i), positions.get(i));
            }
            return map;
        }

        boolean hasDistinctKeys() {
            return new HashSet<>(keys).size() == keys.size();
        }
    }

    // ----- 1. 날짜·경기 시간 -----

    private static LocalDate parseDate(String text, List<String> errors) {
        try {
            return LocalDate.parse(text == null ? "" : text.strip());
        } catch (DateTimeParseException e) {
            errors.add("경기 날짜 형식이 올바르지 않습니다(YYYY-MM-DD).");
            return null;
        }
    }

    private static Integer parseDuration(String text, List<String> errors) {
        try {
            int seconds = SetFingerprint.parseDuration(text == null ? null : text.strip());
            if (seconds <= 0) {
                errors.add("경기 시간은 0보다 커야 합니다.");
                return null;
            }
            return seconds;
        } catch (IllegalArgumentException e) {
            errors.add("경기 시간 형식이 올바르지 않습니다(mm:ss, 예 25:42).");
            return null;
        }
    }

    // ----- 2. 선수 확정 -----

    private static TeamRows resolveRows(String teamName, TeamInput team, PlayerDirectory players, List<String> errors) {
        List<String> keys = new ArrayList<>();
        List<Position> positions = new ArrayList<>();
        int unresolved = 0;
        boolean pending = false;
        for (RowInput row : team.rows()) {
            positions.add(row.position());
            switch (row.resolution()) {
                case NEW -> keys.add(newPlayerKey(teamName, row));
                case EXISTING -> {
                    if (row.playerId() == null || !players.displayNames().containsKey(row.playerId())) {
                        errors.add("존재하지 않는 선수가 연결된 행이 있습니다.");
                        keys.add("unknown:" + teamName + ":" + row.screenRow());
                        pending = true;
                    } else {
                        keys.add(String.valueOf(row.playerId()));
                        if (row.addAlias()) {
                            checkAliasNickname(row.nickname(), errors);
                        }
                    }
                }
                case UNRESOLVED -> {
                    unresolved++;
                    keys.add("unresolved:" + teamName + ":" + row.screenRow());
                    pending = true;
                }
            }
        }
        if (unresolved > 0) {
            errors.add((teamName.equals("top") ? "위쪽" : "아래쪽") + " 팀에 선수를 아직 고르지 않은 행이 "
                    + unresolved + "개 있습니다. 선수를 골라야 합니다.");
        }
        return new TeamRows(keys, positions, pending);
    }

    private static String newPlayerKey(String teamName, RowInput row) {
        return "new:" + teamName + ":" + row.screenRow();
    }

    private static boolean isTruncated(String nickname) {
        return nickname != null && NicknameNormalizer.stripUnicodeSpaces(nickname).endsWith(ELLIPSIS);
    }

    private static boolean isValidLength(String nickname) {
        String normalized = nickname == null ? "" : NicknameNormalizer.normalize(nickname);
        return !normalized.isEmpty() && normalized.codePointCount(0, normalized.length()) <= PlayerAlias.MAX_NICKNAME_LENGTH;
    }

    private static void checkAliasNickname(String nickname, List<String> errors) {
        if (isTruncated(nickname)) {
            errors.add("잘린 닉네임(…)은 별칭으로 추가할 수 없습니다. 전체 닉네임을 입력하세요.");
        } else if (!isValidLength(nickname)) {
            errors.add("별칭으로 추가할 닉네임은 1~" + PlayerAlias.MAX_NICKNAME_LENGTH + "자여야 합니다.");
        }
    }

    // 새 선수: 잘리지 않은 올바른 닉네임이어야 하고, 이미 누군가의 별칭이거나 같은 요청 안에서 겹치면 안 된다
    private static void checkNewNicknames(SetRegistrationRequest request, PlayerDirectory players, List<String> errors) {
        Set<String> seenKeys = new HashSet<>();
        boolean truncatedReported = false;
        boolean lengthReported = false;
        boolean duplicateReported = false;
        boolean takenReported = false;
        for (TeamInput team : List.of(request.top(), request.bottom())) {
            for (RowInput row : team.rows()) {
                if (row.resolution() != RowResolution.NEW) {
                    continue;
                }
                if (isTruncated(row.nickname())) {
                    if (!truncatedReported) {
                        errors.add("잘린 닉네임(…)으로 새 선수를 만들 수 없습니다. 전체 닉네임을 입력하세요.");
                        truncatedReported = true;
                    }
                    continue;
                }
                if (!isValidLength(row.nickname())) {
                    if (!lengthReported) {
                        errors.add("새 선수의 닉네임은 1~" + PlayerAlias.MAX_NICKNAME_LENGTH + "자여야 합니다.");
                        lengthReported = true;
                    }
                    continue;
                }
                String key = NicknameNormalizer.comparisonKey(row.nickname());
                if (!seenKeys.add(key) && !duplicateReported) {
                    errors.add("같은 닉네임으로 새 선수를 두 명 만들 수 없습니다.");
                    duplicateReported = true;
                }
                if (players.aliasOwners().containsKey(key) && !takenReported) {
                    errors.add("새 선수로 만들려는 닉네임이 이미 다른 선수의 별칭입니다. 그 선수를 연결하세요.");
                    takenReported = true;
                }
            }
        }
    }

    // ----- 3. 챔피언 id -----

    private static void checkChampionIds(SetRegistrationRequest request, CatalogSnapshot catalog, List<String> errors) {
        Set<String> unknown = new LinkedHashSet<>();
        for (TeamInput team : List.of(request.top(), request.bottom())) {
            team.rows().forEach(row -> collectUnknown(row.championKey(), catalog, unknown));
            team.bans().forEach(ban -> collectUnknown(ban, catalog, unknown));
        }
        if (!unknown.isEmpty()) {
            errors.add("알 수 없는 챔피언입니다: " + String.join(", ", unknown));
        }
    }

    private static void collectUnknown(String championKey, CatalogSnapshot catalog, Set<String> unknown) {
        if (championKey != null && !championKey.isBlank() && !catalog.contains(championKey)) {
            unknown.add(championKey);
        }
    }

    // ----- 4~5. A팀/B팀 판별과 팀 변경 감지 -----

    private static Judgement judge(SetRegistrationRequest request, DuelState duel, TeamRows top, TeamRows bottom,
            int setNo, List<String> errors) {
        if (setNo == 1) {
            return new Judgement(DuelTeamRules.teamForFirstSet(request.topSide()), DetectionMethod.FIRST_SET, null,
                    false, new TeamChange(Kind.NONE, 0, List.of()));
        }
        DuelState.PreviousSet previous = duel.previousSet();
        boolean canJudge = previous != null && previous.team1().size() == 5 && !top.pending() && top.hasDistinctKeys();
        if (!canJudge) {
            errors.add("선수를 모두 고르면 A팀/B팀을 판별합니다.");
            return new Judgement(null, DetectionMethod.PENDING, null, false,
                    new TeamChange(Kind.PENDING, 0, List.of("선수를 모두 고르면 팀 변경을 감지합니다.")));
        }
        TeamDetection detection = DuelTeamRules.detectTopTeam(previous.team1().keySet(),
                new HashSet<>(top.keys()));
        boolean complete = !bottom.pending() && bottom.hasDistinctKeys();
        TeamChange change = TeamChangeDetector.detect(previous.team1(), previous.team2(), top.byPlayer(),
                complete ? bottom.byPlayer() : null);
        return new Judgement(detection.team(), DetectionMethod.OVERLAP, detection.overlap(),
                change.kind() == Kind.RESHUFFLE, change);
    }

    // 수정: 위쪽 팀이 수정 전 이 세트의 A팀과 3명 이상 겹치면 A팀. 팀 변경 감지는 하지 않는다
    private static Judgement judgeEdit(EditContext edit, TeamRows top, List<String> errors) {
        TeamChange none = new TeamChange(Kind.NONE, 0, List.of());
        if (top.pending() || !top.hasDistinctKeys() || edit.oldTeam1Ids().size() != 5) {
            errors.add("선수를 모두 고르면 A팀/B팀을 판별합니다.");
            return new Judgement(null, DetectionMethod.PENDING, null, false, none);
        }
        TeamDetection detection = DuelTeamRules.detectTopTeam(edit.oldTeam1Ids(), new HashSet<>(top.keys()));
        return new Judgement(detection.team(), DetectionMethod.OVERLAP, detection.overlap(), false, none);
    }

    // 새 A팀이 앞뒤 유효 세트의 A팀과 3명 미만 겹치면 경고(저장은 막지 않는다)
    private static Optional<String> adjacentTeamWarning(EditContext edit, Judgement judgement, TeamRows top,
            TeamRows bottom) {
        if (judgement.topTeam() == null || bottom.pending() || !bottom.hasDistinctKeys()) {
            return Optional.empty();
        }
        Set<String> team1 = new HashSet<>(judgement.topTeam() == DuelTeam.TEAM1 ? top.keys() : bottom.keys());
        for (Set<String> neighbor : Arrays.asList(edit.previousTeam1Ids(), edit.nextTeam1Ids())) {
            if (neighbor != null && neighbor.size() == 5) {
                Set<String> common = new HashSet<>(team1);
                common.retainAll(neighbor);
                if (common.size() < 3) {
                    return Optional.of("앞뒤 세트와 A팀 구성이 맞지 않습니다. 선수 연결을 확인하세요.");
                }
            }
        }
        return Optional.empty();
    }

    // 이 세트의 픽 중 뒤쪽(뒤 번호·뒤 대결) 유효 세트와 겹친 것. 앞쪽 겹침은 도메인 검증이 오류로 낸다
    private static List<Conflict> rearConflicts(SetRegistrationRequest request, EditContext edit) {
        List<String> picks = new ArrayList<>();
        for (TeamInput team : List.of(request.top(), request.bottom())) {
            team.rows().stream().map(RowInput::championKey).filter(key -> key != null && !key.isBlank())
                    .forEach(picks::add);
        }
        int ownSeq = edit.sessionDuels().stream().filter(d -> d.duelId() == edit.duelId()).findFirst()
                .map(ChainDuel::seq).orElse(0);
        return FearlessConflicts.conflicts(edit.sessionDuels(), edit.setId(), edit.duelId(), edit.setNo(), picks,
                edit.otherPicks()).stream()
                .filter(c -> c.duelSeq() > ownSeq || (c.duelId() == edit.duelId() && c.setNo() > edit.setNo()))
                .toList();
    }

    private static List<String> conflictWarnings(List<Conflict> conflicts) {
        Map<Long, List<Conflict>> bySet = new LinkedHashMap<>();
        conflicts.forEach(c -> bySet.computeIfAbsent(c.setId(), id -> new ArrayList<>()).add(c));
        List<String> warnings = new ArrayList<>();
        bySet.values().forEach(group -> warnings.add("뒤쪽 세트(대결 " + group.get(0).duelSeq() + "의 "
                + group.get(0).setNo() + "세트)와 같은 챔피언을 씁니다: "
                + String.join(", ", group.stream().map(Conflict::championKey).toList())));
        return warnings;
    }

    // ----- 6. 도메인 검증용 초안 -----

    private static SetDraft toDraft(SetRegistrationRequest request, TeamRows top, TeamRows bottom) {
        return new SetDraft(request.topSide(), request.capturerPosition(),
                draftTeam(request.top(), top), draftTeam(request.bottom(), bottom),
                bansInTurnOrder(request.topSide(), request.top().bans(), request.bottom().bans()));
    }

    private static DraftTeam draftTeam(TeamInput team, TeamRows rows) {
        List<DraftPlayer> players = new ArrayList<>();
        for (int i = 0; i < team.rows().size(); i++) {
            RowInput row = team.rows().get(i);
            players.add(new DraftPlayer(rows.keys().get(i), row.position(), row.championKey(), row.kills(),
                    row.deaths(), row.assists(), row.gold()));
        }
        return new DraftTeam(players, team.displayedKills(), team.displayedDeaths(), team.displayedAssists(),
                team.displayedGold());
    }

    /** 평가용 밴 10개(B1 R1 ... B5 R5). 빈 칸은 null 그대로 둔다(BanOrder.build는 저장 때만 부른다). */
    static List<String> bansInTurnOrder(Side topSide, List<String> topBans, List<String> bottomBans) {
        List<String> blue = topSide == Side.BLUE ? topBans : bottomBans;
        List<String> red = topSide == Side.BLUE ? bottomBans : topBans;
        List<String> turns = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            turns.add(blue.get(i));
            turns.add(red.get(i));
        }
        return turns;
    }

    // ----- 7. 지문 -----

    private static String fingerprint(SetRegistrationRequest request, LocalDate shotDate, Integer durationSec,
            List<String> errors) {
        TeamTotals top = new TeamTotals(request.top().displayedKills(), request.top().displayedGold());
        TeamTotals bottom = new TeamTotals(request.bottom().displayedKills(), request.bottom().displayedGold());
        try {
            return (durationSec == null ? SetFingerprint.ofUnknownDuration(shotDate, top, bottom)
                    : SetFingerprint.of(shotDate, durationSec, top, bottom)).canonical();
        } catch (IllegalArgumentException e) {
            errors.add("팀 킬·골드 합계가 올바르지 않습니다.");
            return null;
        }
    }
}
