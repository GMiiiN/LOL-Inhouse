package com.lolinhouse.domain.set;

import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.set.SetDraft.DraftPlayer;
import com.lolinhouse.domain.set.SetDraft.DraftTeam;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * 저장 전 세트 초안 검증. errors는 저장을 막고, warnings는 담당자에게 보여주기만 한다(유저플로우 §5.5).
 * 지문 중복은 여기서 검사하지 않는다. 동시 저장까지 막아야 해서 M3에서 DB UNIQUE로 처리한다.
 * 설명: docs/study/06-domain-set-rules.md
 */
public final class SetDraftValidator {

    private static final int PLAYER_COUNT = 10;
    private static final int BAN_COUNT = 10;

    private SetDraftValidator() {
    }

    /**
     * 경고 계산에 필요한 대결 정보.
     *
     * @param setNo               이 세트의 대결 안 순서(1부터)
     * @param topTeam             위쪽 팀이 1팀/2팀 중 어디인지(자동 판별 결과)
     * @param restrictedChampions 피어리스로 제한된 챔피언(이전 세트 픽)
     */
    public record ValidationContext(int setNo, DuelTeam topTeam, Set<String> restrictedChampions) {

        public ValidationContext {
            restrictedChampions = restrictedChampions == null ? Set.of() : Set.copyOf(restrictedChampions);
        }
    }

    /** 검증 결과. errors가 비어 있어야 저장할 수 있다. */
    public record ValidationResult(List<String> errors, List<String> warnings) {

        public ValidationResult {
            errors = SetDraft.unmodifiableCopy(errors);
            warnings = SetDraft.unmodifiableCopy(warnings);
        }

        public boolean canSave() {
            return errors.isEmpty();
        }
    }

    public static ValidationResult validate(SetDraft draft, ValidationContext context) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        checkErrors(draft, errors);
        checkWarnings(draft, context, warnings);
        return new ValidationResult(errors, warnings);
    }

    // ----- 오류 -----

    private static void checkErrors(SetDraft draft, List<String> errors) {
        if (draft.topSide() == null) {
            errors.add("위쪽 팀 진영을 선택해야 합니다.");
        }
        if (draft.capturerPosition() == null) {
            errors.add("캡처한 사람의 포지션을 선택해야 합니다.");
        }
        if (hasEmptyRow(draft.topTeam()) || hasEmptyRow(draft.bottomTeam())) {
            errors.add("비어 있는 선수 행이 있습니다.");
        }
        if (!hasTenDistinctPlayers(draft)) {
            errors.add("선수는 중복 없이 정확히 " + PLAYER_COUNT + "명이어야 합니다.");
        }
        if (!hasOneOfEachPosition(draft.topTeam())) {
            errors.add("위쪽 팀은 탑·정글·미드·원딜·서폿 포지션이 하나씩 있어야 합니다.");
        }
        if (!hasOneOfEachPosition(draft.bottomTeam())) {
            errors.add("아래쪽 팀은 탑·정글·미드·원딜·서폿 포지션이 하나씩 있어야 합니다.");
        }
        long missingPicks = allPlayers(draft).stream().filter(player -> isBlank(player.championKey())).count();
        if (missingPicks > 0) {
            errors.add("픽 챔피언이 비어 있는 선수가 " + missingPicks + "명 있습니다.");
        }
        if (!hasAllBans(draft.bans())) {
            errors.add("밴 " + BAN_COUNT + "개가 모두 채워져야 합니다.");
        }
    }

    private static boolean hasEmptyRow(DraftTeam team) {
        return team != null && team.players().stream().anyMatch(Objects::isNull);
    }

    private static boolean hasTenDistinctPlayers(SetDraft draft) {
        List<DraftPlayer> all = allPlayers(draft);
        Set<String> ids = new HashSet<>();
        for (DraftPlayer player : all) {
            if (isBlank(player.playerId())) {
                return false;
            }
            ids.add(player.playerId());
        }
        return all.size() == PLAYER_COUNT && ids.size() == PLAYER_COUNT;
    }

    private static boolean hasOneOfEachPosition(DraftTeam team) {
        List<DraftPlayer> players = playersOf(team);
        Set<Position> positions = EnumSet.noneOf(Position.class);
        players.stream().map(DraftPlayer::position).filter(Objects::nonNull).forEach(positions::add);
        return players.size() == Position.values().length && positions.size() == Position.values().length;
    }

    private static boolean hasAllBans(List<String> bans) {
        return bans.size() == BAN_COUNT && bans.stream().noneMatch(SetDraftValidator::isBlank);
    }

    // ----- 경고 -----

    private static void checkWarnings(SetDraft draft, ValidationContext context, List<String> warnings) {
        checkTeamTotals("위쪽 팀", draft.topTeam(), warnings);
        checkTeamTotals("아래쪽 팀", draft.bottomTeam(), warnings);
        checkFearless(draft, context, warnings);
        if (draft.topSide() != null && context.topTeam() != null) {
            DuelTeamRules.sideWarning(context.topTeam(), context.setNo(), draft.topSide())
                    .ifPresent(warnings::add);
        }
    }

    private static void checkTeamTotals(String teamName, DraftTeam team, List<String> warnings) {
        if (team == null) {
            return;
        }
        List<DraftPlayer> players = playersOf(team);
        long kills = sum(players, DraftPlayer::kills);
        long deaths = sum(players, DraftPlayer::deaths);
        long assists = sum(players, DraftPlayer::assists);
        long gold = sum(players, DraftPlayer::gold);
        if (kills != team.displayedKills() || deaths != team.displayedDeaths()
                || assists != team.displayedAssists()) {
            warnings.add(teamName + " K/D/A 합계가 결과창 표시와 다릅니다. 선수 행 합 " + kills + "/" + deaths + "/"
                    + assists + ", 표시 " + team.displayedKills() + "/" + team.displayedDeaths() + "/"
                    + team.displayedAssists());
        }
        if (gold != team.displayedGold()) {
            warnings.add(teamName + " 골드 합계가 결과창 표시와 다릅니다. 선수 행 합 " + gold
                    + ", 표시 " + team.displayedGold());
        }
    }

    // int 합이 넘쳐서 표시값과 우연히 같아지지 않도록 long으로 더한다
    private static long sum(List<DraftPlayer> players, ToIntFunction<DraftPlayer> value) {
        return players.stream().mapToLong(value::applyAsInt).sum();
    }

    private static void checkFearless(SetDraft draft, ValidationContext context, List<String> warnings) {
        List<String> picks = allPlayers(draft).stream().map(DraftPlayer::championKey).filter(Objects::nonNull).toList();
        List<String> violations = Fearless.violations(context.restrictedChampions(), picks);
        if (!violations.isEmpty()) {
            warnings.add("피어리스 제한 챔피언을 픽했습니다: " + String.join(", ", violations)
                    + " (인식 오류이거나 초기화를 빠뜨렸을 수 있습니다)");
        }
    }

    // ----- 공통 -----

    /** null 행을 뺀 선수들. null 행은 hasEmptyRow가 오류로 따로 알린다. */
    private static List<DraftPlayer> playersOf(DraftTeam team) {
        return team == null ? List.of() : team.players().stream().filter(Objects::nonNull).toList();
    }

    private static List<DraftPlayer> allPlayers(SetDraft draft) {
        List<DraftPlayer> all = new ArrayList<>(playersOf(draft.topTeam()));
        all.addAll(playersOf(draft.bottomTeam()));
        return all;
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
