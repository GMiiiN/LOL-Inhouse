package com.lolinhouse.registration;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.lolinhouse.champion.CatalogSnapshot;
import com.lolinhouse.champion.ChampionCatalog;
import com.lolinhouse.common.web.ApiException;
import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.set.BanOrder;
import com.lolinhouse.domain.set.BanOrder.BanTurn;
import com.lolinhouse.domain.set.SetFingerprint;
import com.lolinhouse.ingest.extract.ExtractedScoreboard.TopResult;
import com.lolinhouse.ingest.job.ExtractionJob;
import com.lolinhouse.ingest.job.ExtractionJobRepository;
import com.lolinhouse.ingest.job.ExtractionJobStatus;
import com.lolinhouse.ingest.job.ExtractionResult;
import com.lolinhouse.player.Player;
import com.lolinhouse.registration.SetRegistrationResponse.PlayerRef;
import com.lolinhouse.player.PlayerService;
import com.lolinhouse.session.Duel;
import com.lolinhouse.session.DuelRepository;
import com.lolinhouse.session.GameSet;
import com.lolinhouse.session.GameSet.DisplayedTotals;
import com.lolinhouse.session.GameSetRepository;
import com.lolinhouse.session.InhouseSessionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * 역할: 세트 등록 유스케이스 — 초안(draft), 읽기 전용 검사(check), 저장(register).
 * 저장 순서: (대결 또는 묶음) 행 잠금 → 작업 행 잠금 → 평가 → 선수·별칭 확정 → 세트 저장 → 작업 연결. 한 트랜잭션이라 실패하면 모두 되돌린다.
 * 지문·세트 번호·작업 1:1 UNIQUE 위반은 트랜잭션이 끝난 뒤에 제약 이름으로 구분해 409로 바꾼다(트랜잭션 안에서는 이미 망가져 있어 조회가 안 된다).
 * "새 대결로 등록"(newDuel)은 같은 트랜잭션에서 묶음 행을 잠그고 다음 번호 대결을 만든 뒤 작업을 옮겨 새 대결 1세트로 저장한다.
 * 설명: docs/design/m4-design.md §4.2, §11.1
 */
@Service
public class SetRegistrationService {

    private static final Pattern CONSTRAINT_NAME = Pattern.compile("for key '([^']+)'");

    private final DuelRepository duelRepository;
    private final InhouseSessionRepository sessionRepository;
    private final GameSetRepository gameSetRepository;
    private final ExtractionJobRepository jobRepository;
    private final RegistrationContextService contextService;
    private final SetEvaluator evaluator;
    private final DraftService draftService;
    private final PlayerService playerService;
    private final ChampionCatalog championCatalog;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate transactionTemplate;

    public SetRegistrationService(DuelRepository duelRepository, InhouseSessionRepository sessionRepository,
            GameSetRepository gameSetRepository, ExtractionJobRepository jobRepository,
            RegistrationContextService contextService, SetEvaluator evaluator, DraftService draftService,
            PlayerService playerService, ChampionCatalog championCatalog, JsonMapper jsonMapper,
            TransactionTemplate transactionTemplate) {
        this.duelRepository = duelRepository;
        this.sessionRepository = sessionRepository;
        this.gameSetRepository = gameSetRepository;
        this.jobRepository = jobRepository;
        this.contextService = contextService;
        this.evaluator = evaluator;
        this.draftService = draftService;
        this.playerService = playerService;
        this.championCatalog = championCatalog;
        this.jsonMapper = jsonMapper;
        this.transactionTemplate = transactionTemplate;
    }

    /** 성공한 추출 작업의 초안. 아직 저장 안 된 작업만. */
    public DraftResponse draft(Long jobId) {
        CatalogSnapshot catalog = requireCatalog();
        return transactionTemplate.execute(status -> {
            ExtractionJob job = jobRepository.findById(jobId).orElseThrow(SetRegistrationService::jobNotFound);
            requireReadyAndUnsaved(job);
            ExtractionResult result = jsonMapper.readValue(job.resultJson(), ExtractionResult.class);
            Duel duel = duelRepository.findById(job.duelId()).orElseThrow(() -> duelNotFound(job.duelId()));
            return draftService.build(job, result, contextService.stateFor(duel), contextService.directory(), catalog,
                    this::findDuplicate);
        });
    }

    /** 저장 없는 검사. 아무것도 쓰지 않는다. */
    public Evaluation check(Long duelId, SetRegistrationRequest request) {
        RegistrationShape.validate(request);
        CatalogSnapshot catalog = requireCatalog();
        return transactionTemplate.execute(status -> {
            Duel duel = duelRepository.findById(duelId).orElseThrow(() -> duelNotFound(duelId));
            DuelState state = request.newDuel() == null ? contextService.stateFor(duel)
                    : contextService.stateForNewDuel(duel, request.newDuel().fearlessMode());
            return evaluator.evaluate(request, state, contextService.directory(), catalog, this::findDuplicate);
        });
    }

    public SetRegistrationResponse register(Long duelId, SetRegistrationRequest request) {
        RegistrationShape.validate(request);
        CatalogSnapshot catalog = requireCatalog();
        // 잠금 읽기 전에 이 트랜잭션이 일반 SELECT로 스냅샷을 만들지 않도록, 대결 존재 확인은 트랜잭션 밖에서 한다
        Duel original = duelRepository.findById(duelId).orElseThrow(() -> duelNotFound(duelId));
        AtomicReference<String> fingerprint = new AtomicReference<>();
        try {
            return transactionTemplate.execute(status -> doRegister(original, request, catalog, fingerprint));
        } catch (DataIntegrityViolationException e) {
            throw translate(e, fingerprint.get());
        }
    }

    private SetRegistrationResponse doRegister(Duel original, SetRegistrationRequest request, CatalogSnapshot catalog,
            AtomicReference<String> fingerprint) {
        boolean newDuel = request.newDuel() != null;
        // 잠금 순서는 모든 저장 경로에서 같다: 묶음 → (일반이면) 대결 → 작업. 묶음을 먼저 잠가야 이어가기(CONTINUE) 체인의
        // 다른 대결에서 진행 중인 미커밋 픽을 피어리스 검사가 놓치지 않는다
        sessionRepository.findByIdForUpdate(original.sessionId()).orElseThrow(() -> duelNotFound(original.id()));
        if (!newDuel) {
            duelRepository.findByIdForUpdate(original.id()).orElseThrow(() -> duelNotFound(original.id()));
        }
        ExtractionJob job = jobRepository.findByIdForUpdate(request.extractionJobId())
                .orElseThrow(SetRegistrationService::jobNotFound);
        if (!job.duelId().equals(original.id())) {
            throw ApiException.badRequest("EXTRACTION_DUEL_MISMATCH", "다른 대결에서 추출한 작업입니다.");
        }
        requireReadyAndUnsaved(job);

        Long targetDuelId = original.id();
        DuelState state;
        if (newDuel) {
            state = contextService.stateForNewDuel(original, request.newDuel().fearlessMode());
            int nextSeq = duelRepository.findMaxSeq(original.sessionId()) + 1;
            Duel created = duelRepository.saveAndFlush(
                    Duel.of(original.sessionId(), nextSeq, request.newDuel().fearlessMode()));
            job.moveToDuel(created.id());
            targetDuelId = created.id();
        } else {
            state = contextService.stateFor(original);
        }

        Evaluation evaluation = evaluator.evaluate(request, state, contextService.directory(), catalog,
                this::findDuplicate);
        fingerprint.set(evaluation.fingerprint());
        if (evaluation.duplicateSet() != null) {
            throw duplicateSet(evaluation.duplicateSet());
        }
        if (!evaluation.errors().isEmpty()) {
            throw new SetRulesViolatedException(evaluation.errors(), evaluation.warnings());
        }

        List<PlayerRef> createdPlayers = new ArrayList<>();
        List<PlayerRef> addedAliases = new ArrayList<>();
        Long[] topIds = confirmPlayers(request.top(), createdPlayers, addedAliases);
        Long[] bottomIds = confirmPlayers(request.bottom(), createdPlayers, addedAliases);

        GameSet set = buildSet(targetDuelId, request, evaluation, job, topIds, bottomIds);
        set = gameSetRepository.saveAndFlush(set);
        job.linkGameSet(set.id());
        jobRepository.saveAndFlush(job);
        return new SetRegistrationResponse(set.id(), targetDuelId, set.setNo(), evaluation.topTeam(),
                evaluation.warnings(), createdPlayers, addedAliases);
    }

    // 새 선수를 먼저 만들고(NEW), 별칭 추가를 요청한 기존 선수에게 별칭을 붙인다. 충돌은 PlayerService가 409로 던져 전체가 되돌아간다
    private Long[] confirmPlayers(TeamInput team, List<PlayerRef> created, List<PlayerRef> aliases) {
        Long[] ids = new Long[team.rows().size()];
        for (int i = 0; i < ids.length; i++) {
            RowInput row = team.rows().get(i);
            if (row.resolution() == RowResolution.NEW) {
                Player player = playerService.createUnregistered(row.nickname());
                ids[i] = player.id();
                created.add(new PlayerRef(player.id(), player.gameNickname()));
            } else {
                ids[i] = row.playerId();
                if (row.addAlias()) {
                    playerService.addAliasIfAbsent(row.playerId(), row.nickname())
                            .ifPresent(alias -> aliases.add(new PlayerRef(row.playerId(), alias.nickname())));
                }
            }
        }
        return ids;
    }

    private GameSet buildSet(Long duelId, SetRegistrationRequest request, Evaluation evaluation, ExtractionJob job,
            Long[] topIds, Long[] bottomIds) {
        DuelTeam topTeam = evaluation.topTeam();
        DuelTeam bottomTeam = topTeam.other();
        TeamInput team1 = topTeam == DuelTeam.TEAM1 ? request.top() : request.bottom();
        TeamInput team2 = topTeam == DuelTeam.TEAM1 ? request.bottom() : request.top();
        DuelTeam winner = request.topResult() == TopResult.WIN ? topTeam : bottomTeam;
        GameSet set = GameSet.create(duelId, evaluation.setNo(), evaluation.team1Side(), winner, topTeam,
                LocalDate.parse(request.shotDate().strip()), SetFingerprint.parseDuration(request.duration().strip()),
                totals(team1), totals(team2), job.imageHash(), job.imageKey());
        addPlayers(set, topTeam, request.top(), topIds);
        addPlayers(set, bottomTeam, request.bottom(), bottomIds);
        for (BanTurn turn : BanOrder.build(request.topSide(), request.top().bans(), request.bottom().bans())) {
            set.addBan(turn.turn(), turn.side(), turn.championKey());
        }
        return set;
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

    private RuntimeException translate(DataIntegrityViolationException e, String fingerprint) {
        if (violatesPlayerForeignKey(e)) {
            // 확인한 선수가 저장 직전에 삭제된 경우(MySQL 1452). 데이터는 FK가 지켰고 사용자는 선수 선택을 다시 확인하면 된다
            return ApiException.conflict("CONCURRENT_REQUEST_CONFLICT", "선택한 선수가 삭제되었습니다. 선수를 다시 확인하세요.");
        }
        String constraint = constraintName(e);
        if (constraint == null) {
            return e;
        }
        if (constraint.endsWith("uk_game_set_active_fingerprint")) {
            return fingerprint == null ? ApiException.conflict("DUPLICATE_SET", "이미 등록된 경기입니다.")
                    : transactionTemplate.execute(status -> findDuplicate(fingerprint)
                            .map(SetRegistrationService::duplicateSet)
                            .orElseGet(() -> ApiException.conflict("DUPLICATE_SET", "이미 등록된 경기입니다.")));
        }
        if (constraint.endsWith("uk_game_set_duel_active_set_no")) {
            return ApiException.conflict("SET_NO_CONFLICT", "다른 요청이 같은 세트 번호를 먼저 저장했습니다. 화면을 새로 고친 뒤 다시 저장하세요.");
        }
        if (constraint.endsWith("uk_extraction_job_game_set")) {
            return ApiException.conflict("EXTRACTION_ALREADY_SAVED", "이미 세트로 저장된 추출 작업입니다.");
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

    // MySQL 1062 메시지의 "for key '...'"에서 제약 이름만 꺼낸다. 메시지에는 닉네임 키가 섞일 수 있어 로그에 남기지 않는다
    private static String constraintName(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null) {
                Matcher matcher = CONSTRAINT_NAME.matcher(message);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return null;
    }

    private static ApiException duplicateSet(DuplicateSet existing) {
        return ApiException.conflict("DUPLICATE_SET", "이미 등록된 경기입니다.")
                .with("existingSetId", existing.setId())
                .with("existingDuelId", existing.duelId())
                .with("existingSetNo", existing.setNo());
    }

    private java.util.Optional<DuplicateSet> findDuplicate(String fingerprint) {
        return gameSetRepository.findByFingerprintAndDeletedAtIsNull(fingerprint)
                .map(set -> new DuplicateSet(set.id(), set.duelId(), set.setNo()));
    }

    private void requireReadyAndUnsaved(ExtractionJob job) {
        if (job.gameSetId() != null) {
            throw ApiException.conflict("EXTRACTION_ALREADY_SAVED", "이미 세트로 저장된 추출 작업입니다.")
                    .with("savedSetId", job.gameSetId());
        }
        if (job.status() != ExtractionJobStatus.SUCCEEDED) {
            throw ApiException.conflict("EXTRACTION_NOT_READY", "추출이 아직 끝나지 않았거나 실패한 작업입니다.");
        }
    }

    private CatalogSnapshot requireCatalog() {
        return championCatalog.current().orElseThrow(ChampionCatalogUnavailableException::new);
    }

    private static ApiException jobNotFound() {
        return ApiException.notFound("EXTRACTION_NOT_FOUND", "추출 작업을 찾을 수 없습니다.");
    }

    private static ApiException duelNotFound(Long duelId) {
        return ApiException.notFound("DUEL_NOT_FOUND", "대결을 찾을 수 없습니다: " + duelId);
    }
}
