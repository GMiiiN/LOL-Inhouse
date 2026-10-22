package com.lolinhouse.edit;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import com.lolinhouse.champion.CatalogSnapshot;
import com.lolinhouse.champion.ChampionCatalog;
import com.lolinhouse.common.web.ApiException;
import com.lolinhouse.common.web.PublicReadErrorCodes;
import com.lolinhouse.common.web.RecordEditErrorCodes;
import com.lolinhouse.common.web.UniqueViolations;
import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Side;
import com.lolinhouse.domain.set.BanOrder;
import com.lolinhouse.domain.set.BanOrder.BanTurn;
import com.lolinhouse.domain.set.BanOrder.TeamBans;
import com.lolinhouse.domain.set.FearlessChain.ChainDuel;
import com.lolinhouse.domain.set.FearlessConflicts;
import com.lolinhouse.domain.set.FearlessConflicts.Conflict;
import com.lolinhouse.domain.set.FearlessConflicts.SetPick;
import com.lolinhouse.domain.set.SetFingerprint;
import com.lolinhouse.ingest.extract.ExtractedScoreboard.TopResult;
import com.lolinhouse.player.Player;
import com.lolinhouse.player.PlayerRepository;
import com.lolinhouse.registration.DuplicateLookup;
import com.lolinhouse.registration.DuplicateSet;
import com.lolinhouse.registration.EditContext;
import com.lolinhouse.registration.EditResult;
import com.lolinhouse.registration.Evaluation;
import com.lolinhouse.registration.RegistrationContextService;
import com.lolinhouse.registration.RegistrationShape;
import com.lolinhouse.registration.RowInput;
import com.lolinhouse.registration.RowResolution;
import com.lolinhouse.registration.SetEvaluator;
import com.lolinhouse.registration.SetRegistrationResponse.PlayerRef;
import com.lolinhouse.registration.SetRegistrationService;
import com.lolinhouse.registration.TeamInput;
import com.lolinhouse.session.Duel;
import com.lolinhouse.session.DuelRepository;
import com.lolinhouse.session.FearlessMode;
import com.lolinhouse.session.GameSet;
import com.lolinhouse.session.GameSet.DisplayedTotals;
import com.lolinhouse.session.GameSetPlayer;
import com.lolinhouse.session.GameSetRepository;
import com.lolinhouse.session.GameSetRepository.PickProjection;
import com.lolinhouse.session.InhouseSession;
import com.lolinhouse.session.InhouseSessionRepository;
import com.lolinhouse.tier.ScoreSettingRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 역할: 저장된 세트의 수정 폼 펼치기·검사·저장(설계 docs/design/m6-design.md §4.1, §5).
 * 저장 순서: 묶음 → 대결 → 세트 잠금 → 삭제·낡음 확인 → 평가 → (새 선수·별칭이면) score_setting 잠금 → 선수·별칭 확정 →
 * 메타 갱신·지문 재계산 → 자식 비우기 → flush → 다시 추가 → flush. 자식을 비운 뒤 flush하지 않으면 INSERT가 DELETE보다 먼저 나가
 * 같은 팀 포지션 맞바꿈이나 두 팀 사이 선수 바꿈이 UNIQUE에 걸린다.
 * UNIQUE·FK 위반은 트랜잭션이 끝난 뒤 제약 이름으로 409로 바꾼다.
 */
@Service
public class SetEditService {

    private static final String FINGERPRINT_CONSTRAINT = "uk_game_set_active_fingerprint";
    private static final String PLAYER_UNIQUE_CONSTRAINT = "uk_game_set_player_player";

    private final GameSetRepository gameSets;
    private final DuelRepository duels;
    private final InhouseSessionRepository sessions;
    private final PlayerRepository playerRepository;
    private final ScoreSettingRepository scoreSettings;
    private final RegistrationContextService contextService;
    private final SetRegistrationService registrationService;
    private final SetEvaluator evaluator;
    private final ChampionCatalog championCatalog;
    private final TransactionTemplate tx;

    public SetEditService(GameSetRepository gameSets, DuelRepository duels, InhouseSessionRepository sessions,
            PlayerRepository playerRepository, ScoreSettingRepository scoreSettings,
            RegistrationContextService contextService, SetRegistrationService registrationService,
            SetEvaluator evaluator, ChampionCatalog championCatalog, TransactionTemplate tx) {
        this.gameSets = gameSets;
        this.duels = duels;
        this.sessions = sessions;
        this.playerRepository = playerRepository;
        this.scoreSettings = scoreSettings;
        this.contextService = contextService;
        this.registrationService = registrationService;
        this.evaluator = evaluator;
        this.championCatalog = championCatalog;
        this.tx = tx;
    }

    /** 삭제된 세트도 읽을 수 있다(deleted=true). */
    public SetFormResponse form(long setId) {
        CatalogSnapshot catalog = requireCatalog();
        return tx.execute(status -> {
            GameSet set = gameSets.findById(setId).orElseThrow(() -> setNotFound(setId));
            Duel duel = duels.findById(set.duelId()).orElseThrow(() -> setNotFound(setId));
            InhouseSession session = sessions.findById(duel.sessionId()).orElseThrow(() -> setNotFound(setId));
            EditContext context = context(set, duel);
            SetEditRequest form = toForm(set);
            EditResult result = evaluator.evaluateEdit(form.toRegistration(), context, contextService.directory(),
                    catalog, lookupExcluding(setId));
            List<String> restricted = FearlessConflicts.restrictedAt(context.sessionDuels(), duel.id(), set.setNo(),
                    context.otherPicks());
            EditEvaluation evaluation = EditEvaluation.of(result.evaluation(), result.fearlessConflicts());
            return new SetFormResponse(set.id(), duel.id(), duel.seq(), session.id(), session.startDate(),
                    set.setNo(), set.isDeleted(), set.updatedAt(), set.imageKey() != null, form, evaluation,
                    restricted, evaluation.fearlessConflicts());
        });
    }

    public EditEvaluation check(long setId, SetEditRequest request) {
        RegistrationShape.validate(request.toRegistration());
        CatalogSnapshot catalog = requireCatalog();
        return tx.execute(status -> {
            GameSet set = gameSets.findById(setId).orElseThrow(() -> setNotFound(setId));
            if (set.isDeleted()) {
                throw setDeleted();
            }
            Duel duel = duels.findById(set.duelId()).orElseThrow(() -> setNotFound(setId));
            EditResult result = evaluator.evaluateEdit(request.toRegistration(), context(set, duel),
                    contextService.directory(), catalog, lookupExcluding(setId));
            return EditEvaluation.of(result.evaluation(), result.fearlessConflicts());
        });
    }

    public SetEditResponse update(long setId, SetEditRequest request) {
        RegistrationShape.validate(request.toRegistration());
        CatalogSnapshot catalog = requireCatalog();
        // 잠금 읽기 전에 이 트랜잭션이 일반 SELECT로 스냅샷을 만들지 않도록, 세트·대결 확인은 트랜잭션 밖에서 한다
        GameSet found = gameSets.findById(setId).orElseThrow(() -> setNotFound(setId));
        Duel duel = duels.findById(found.duelId()).orElseThrow(() -> setNotFound(setId));
        AtomicReference<String> fingerprint = new AtomicReference<>();
        try {
            return tx.execute(status -> doUpdate(duel, setId, request, catalog, fingerprint));
        } catch (DataIntegrityViolationException e) {
            throw translate(e, setId, fingerprint.get());
        }
    }

    private SetEditResponse doUpdate(Duel duel, long setId, SetEditRequest request, CatalogSnapshot catalog,
            AtomicReference<String> fingerprint) {
        sessions.findByIdForUpdate(duel.sessionId()).orElseThrow(() -> setNotFound(setId));
        duels.findByIdForUpdate(duel.id()).orElseThrow(() -> setNotFound(setId));
        GameSet set = gameSets.findByIdForUpdate(setId).orElseThrow(() -> setNotFound(setId));
        if (set.isDeleted()) {
            throw setDeleted();
        }
        if (!set.updatedAt().equals(request.expectedUpdatedAt())) {
            throw ApiException.conflict(RecordEditErrorCodes.SET_MODIFIED,
                    "다른 곳에서 이 세트가 먼저 수정되었습니다. 새로 불러온 뒤 다시 수정하세요.");
        }

        EditResult result = evaluator.evaluateEdit(request.toRegistration(), context(set, duel),
                contextService.directory(), catalog, lookupExcluding(setId));
        Evaluation evaluation = result.evaluation();
        fingerprint.set(evaluation.fingerprint());
        if (evaluation.duplicateSet() != null) {
            throw duplicateSet(evaluation.duplicateSet());
        }
        if (!evaluation.errors().isEmpty()) {
            throw ApiException.unprocessable("SET_RULES_VIOLATED", "세트가 규칙을 어겨 저장할 수 없습니다.")
                    .with("violations", evaluation.errors()).with("warnings", evaluation.warnings());
        }

        // 선수 합치기와 같은 순서로 잠근다: score_setting → (선수 생성·별칭) → 자식 행 변경
        if (createsPlayersOrAliases(request)) {
            scoreSettings.findForUpdate();
        }
        List<PlayerRef> createdPlayers = new ArrayList<>();
        List<PlayerRef> addedAliases = new ArrayList<>();
        Long[] topIds = registrationService.confirmPlayers(request.top(), createdPlayers, addedAliases);
        Long[] bottomIds = registrationService.confirmPlayers(request.bottom(), createdPlayers, addedAliases);

        DuelTeam topTeam = evaluation.topTeam();
        DuelTeam bottomTeam = topTeam.other();
        TeamInput team1 = topTeam == DuelTeam.TEAM1 ? request.top() : request.bottom();
        TeamInput team2 = topTeam == DuelTeam.TEAM1 ? request.bottom() : request.top();
        DuelTeam winner = request.topResult() == TopResult.WIN ? topTeam : bottomTeam;
        Integer durationSec = request.duration().isBlank() ? null
                : SetFingerprint.parseDuration(request.duration().strip());

        set.revise(evaluation.team1Side(), winner, topTeam, LocalDate.parse(request.shotDate().strip()), durationSec,
                totals(team1), totals(team2));
        set.clearRecords();
        gameSets.saveAndFlush(set);

        addPlayers(set, topTeam, request.top(), topIds);
        addPlayers(set, bottomTeam, request.bottom(), bottomIds);
        for (BanTurn turn : BanOrder.build(request.topSide(), request.top().bans(), request.bottom().bans())) {
            set.addBan(turn.turn(), turn.side(), turn.championKey());
        }
        gameSets.saveAndFlush(set);
        return new SetEditResponse(set.id(), duel.id(), set.setNo(), topTeam, evaluation.warnings(),
                result.fearlessConflicts(), createdPlayers, addedAliases);
    }

    // ----- 폼 펼치기 -----

    private SetEditRequest toForm(GameSet set) {
        DuelTeam topTeam = set.topTeam() == null ? DuelTeam.TEAM1 : set.topTeam();
        Side topSide = topTeam == DuelTeam.TEAM1 ? set.team1Side() : set.team1Side().opposite();
        TopResult topResult = set.winnerTeam() == topTeam ? TopResult.WIN : TopResult.LOSS;
        List<GameSetPlayer> records = set.players();
        Map<Long, Player> byId = playerRepository
                .findAllById(records.stream().map(GameSetPlayer::playerId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Player::id, p -> p));
        TeamBans bans = BanOrder.split(topSide, set.bans().stream()
                .map(b -> new BanTurn(b.turn(), b.side(), b.championKey())).toList());
        DisplayedTotals topTotals = topTeam == DuelTeam.TEAM1 ? set.team1Totals() : set.team2Totals();
        DisplayedTotals bottomTotals = topTeam == DuelTeam.TEAM1 ? set.team2Totals() : set.team1Totals();
        return new SetEditRequest(topSide, topResult, set.shotDate().toString(), durationText(set.durationSec()),
                teamInput(records, topTeam, topTotals, bans.top(), byId),
                teamInput(records, topTeam.other(), bottomTotals, bans.bottom(), byId), set.updatedAt());
    }

    private static String durationText(Integer seconds) {
        return seconds == null ? "" : String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    // 행 순서는 저장된 화면 행 번호. 번호가 없는 옛 행이 있으면 포지션 순으로 1~5를 새로 매긴다
    private static TeamInput teamInput(List<GameSetPlayer> records, DuelTeam team, DisplayedTotals totals,
            List<String> bans, Map<Long, Player> players) {
        List<GameSetPlayer> mine = new ArrayList<>(records.stream().filter(p -> p.team() == team).toList());
        boolean numbered = mine.stream().allMatch(p -> p.screenRow() != null);
        mine.sort(numbered ? Comparator.comparing(GameSetPlayer::screenRow) : Comparator.comparing(GameSetPlayer::position));
        List<RowInput> rows = new ArrayList<>();
        for (int i = 0; i < mine.size(); i++) {
            GameSetPlayer p = mine.get(i);
            rows.add(new RowInput(numbered ? p.screenRow() : i + 1, p.position(),
                    players.get(p.playerId()).gameNickname(), RowResolution.EXISTING, p.playerId(), false,
                    p.championKey(), p.kills(), p.deaths(), p.assists(), p.cs(), p.gold()));
        }
        return new TeamInput(totals.kills(), totals.deaths(), totals.assists(), totals.gold(), rows,
                new ArrayList<>(bans));
    }

    // ----- 평가 맥락 -----

    private EditContext context(GameSet set, Duel duel) {
        List<Duel> sessionDuels = duels.findBySessionIdOrderBySeqAsc(duel.sessionId());
        List<ChainDuel> chain = sessionDuels.stream()
                .map(d -> new ChainDuel(d.id(), d.seq(), d.fearlessMode() == FearlessMode.CONTINUE)).toList();
        List<SetPick> otherPicks = new ArrayList<>();
        for (PickProjection p : gameSets.findActivePicksByDuelIds(sessionDuels.stream().map(Duel::id).toList())) {
            if (!p.getSetId().equals(set.id())) {
                otherPicks.add(new SetPick(p.getSetId(), p.getDuelId(), p.getSetNo(), p.getChampionKey()));
            }
        }
        GameSet previous = null;
        GameSet next = null;
        for (GameSet other : gameSets.findByDuelIdAndDeletedAtIsNullOrderBySetNoAsc(duel.id())) {
            if (other.setNo() < set.setNo()) {
                previous = other;
            } else if (other.setNo() > set.setNo() && next == null) {
                next = other;
            }
        }
        return new EditContext(set.id(), duel.id(), set.setNo(), team1Ids(set), previous == null ? null : team1Ids(previous),
                next == null ? null : team1Ids(next), chain, otherPicks);
    }

    private static Set<String> team1Ids(GameSet set) {
        return set.players().stream().filter(p -> p.team() == DuelTeam.TEAM1).map(p -> String.valueOf(p.playerId()))
                .collect(Collectors.toSet());
    }

    private DuplicateLookup lookupExcluding(long setId) {
        return fingerprint -> gameSets.findByFingerprintAndDeletedAtIsNull(fingerprint)
                .filter(other -> !other.id().equals(setId))
                .map(other -> new DuplicateSet(other.id(), other.duelId(), other.setNo()));
    }

    // ----- 저장 보조 -----

    private static boolean createsPlayersOrAliases(SetEditRequest request) {
        return List.of(request.top(), request.bottom()).stream().flatMap(t -> t.rows().stream())
                .anyMatch(row -> row.resolution() == RowResolution.NEW || row.addAlias());
    }

    private static void addPlayers(GameSet set, DuelTeam team, TeamInput input, Long[] ids) {
        for (int i = 0; i < ids.length; i++) {
            RowInput row = input.rows().get(i);
            set.addPlayer(ids[i], team, row.position(), row.screenRow(), row.championKey(), row.kills(), row.deaths(),
                    row.assists(), row.cs(), row.gold());
        }
    }

    private static DisplayedTotals totals(TeamInput team) {
        return new DisplayedTotals(team.displayedKills(), team.displayedDeaths(), team.displayedAssists(),
                team.displayedGold());
    }

    // ----- 오류 변환 -----

    private RuntimeException translate(DataIntegrityViolationException e, long setId, String fingerprint) {
        if (violatesPlayerForeignKey(e)) {
            return ApiException.conflict("CONCURRENT_REQUEST_CONFLICT", "선택한 선수가 삭제되었습니다. 선수를 다시 확인하세요.");
        }
        String constraint = UniqueViolations.constraintName(e);
        if (constraint == null) {
            return e;
        }
        if (constraint.endsWith(FINGERPRINT_CONSTRAINT)) {
            ApiException existing = fingerprint == null ? null : tx.execute(
                    status -> lookupExcluding(setId).find(fingerprint).map(SetEditService::duplicateSet).orElse(null));
            return existing != null ? existing : ApiException.conflict("DUPLICATE_SET", "이미 등록된 경기입니다.");
        }
        if (constraint.endsWith(PLAYER_UNIQUE_CONSTRAINT)) {
            return ApiException.conflict("CONCURRENT_REQUEST_CONFLICT", "다른 요청과 겹쳤습니다. 화면을 새로 고친 뒤 다시 저장하세요.");
        }
        return e;
    }

    private static boolean violatesPlayerForeignKey(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains("fk_game_set_player_player")) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    private static ApiException duplicateSet(DuplicateSet existing) {
        return ApiException.conflict("DUPLICATE_SET", "이미 등록된 경기입니다.")
                .with("existingSetId", existing.setId())
                .with("existingDuelId", existing.duelId())
                .with("existingSetNo", existing.setNo());
    }

    private static ApiException setDeleted() {
        return ApiException.conflict(RecordEditErrorCodes.SET_DELETED, "삭제된 세트는 수정할 수 없습니다. 먼저 복구하세요.");
    }

    private static ApiException setNotFound(long id) {
        return ApiException.notFound(PublicReadErrorCodes.SET_NOT_FOUND, "세트를 찾을 수 없습니다: " + id);
    }

    private CatalogSnapshot requireCatalog() {
        return championCatalog.current().orElseThrow(() -> ApiException.serviceUnavailable(
                "CHAMPION_CATALOG_UNAVAILABLE", "챔피언 목록을 불러올 수 없습니다. 잠시 후 다시 시도하세요."));
    }
}
