package com.lolinhouse.edit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.lolinhouse.common.web.ApiException;
import com.lolinhouse.common.web.PublicReadErrorCodes;
import com.lolinhouse.common.web.RecordEditErrorCodes;
import com.lolinhouse.common.web.UniqueViolations;
import com.lolinhouse.domain.set.FearlessChain.ChainDuel;
import com.lolinhouse.domain.set.FearlessConflicts;
import com.lolinhouse.domain.set.FearlessConflicts.Conflict;
import com.lolinhouse.domain.set.FearlessConflicts.SetPick;
import com.lolinhouse.session.Duel;
import com.lolinhouse.session.DuelRepository;
import com.lolinhouse.session.FearlessMode;
import com.lolinhouse.session.GameSet;
import com.lolinhouse.session.GameSetRepository;
import com.lolinhouse.session.GameSetRepository.PickProjection;
import com.lolinhouse.session.InhouseSession;
import com.lolinhouse.session.InhouseSessionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 역할: 저장된 세트의 소프트 삭제·복구·삭제 목록(설계 docs/design/m6-design.md §4.3·§4.4).
 * 잠금 순서는 등록과 같다: 묶음 → 대결 → 세트. 대결·묶음 id는 잠금 읽기 전에 트랜잭션 밖에서 읽어 둔다.
 * 복구의 지문·번호 충돌은 사전 조회로 친절한 409를 주고, 동시 요청으로 DB UNIQUE가 걸리면 트랜잭션이 끝난 뒤 제약 이름으로 같은 409로 바꾼다.
 */
@Service
public class SetLifecycleService {

    private static final String FINGERPRINT_CONSTRAINT = "uk_game_set_active_fingerprint";
    private static final String SET_NO_CONSTRAINT = "uk_game_set_duel_active_set_no";

    private final GameSetRepository gameSets;
    private final DuelRepository duels;
    private final InhouseSessionRepository sessions;
    private final TransactionTemplate tx;

    public SetLifecycleService(GameSetRepository gameSets, DuelRepository duels, InhouseSessionRepository sessions,
            TransactionTemplate tx) {
        this.gameSets = gameSets;
        this.duels = duels;
        this.sessions = sessions;
        this.tx = tx;
    }

    public void delete(long setId) {
        Duel duel = duelOf(setId);
        tx.executeWithoutResult(status -> {
            GameSet set = lockAll(duel, setId);
            if (set.isDeleted()) {
                throw ApiException.conflict(RecordEditErrorCodes.SET_ALREADY_DELETED, "이미 삭제된 세트입니다.");
            }
            set.markDeleted();
            gameSets.saveAndFlush(set);
        });
    }

    public RestoreResponse restore(long setId) {
        Duel duel = duelOf(setId);
        try {
            return tx.execute(status -> doRestore(duel, setId));
        } catch (DataIntegrityViolationException e) {
            throw translateRestore(e, setId);
        }
    }

    private RestoreResponse doRestore(Duel duel, long setId) {
        GameSet set = lockAll(duel, setId);
        if (!set.isDeleted()) {
            throw ApiException.conflict(RecordEditErrorCodes.SET_NOT_DELETED, "삭제되지 않은 세트입니다.");
        }
        gameSets.findByFingerprintAndDeletedAtIsNull(set.fingerprint()).ifPresent(existing -> {
            throw fingerprintConflict(existing);
        });
        gameSets.findByDuelIdAndDeletedAtIsNullOrderBySetNoAsc(duel.id()).stream()
                .filter(other -> other.setNo() == set.setNo()).findFirst().ifPresent(existing -> {
                    throw setNoConflict(existing.id());
                });
        set.restore();
        gameSets.saveAndFlush(set);

        List<Duel> sessionDuels = duels.findBySessionIdOrderBySeqAsc(duel.sessionId());
        List<ChainDuel> chainDuels = sessionDuels.stream().map(SetLifecycleService::chainDuel).toList();
        List<SetPick> validPicks = toPicks(
                gameSets.findActivePicksByDuelIds(sessionDuels.stream().map(Duel::id).toList()));
        List<String> ownPicks = validPicks.stream().filter(p -> p.setId() == setId).map(SetPick::championKey)
                .toList();
        List<Conflict> conflicts = FearlessConflicts.conflicts(chainDuels, setId, duel.id(), set.setNo(), ownPicks,
                validPicks);
        return new RestoreResponse(setId, warningsOf(conflicts), conflicts);
    }

    /** 삭제된 세트 전부, 최근 삭제순. 쿼리 수는 세트 수와 무관하게 고정이다. */
    public List<DeletedSetRow> deletedSets() {
        return tx.execute(status -> buildDeletedRows());
    }

    private List<DeletedSetRow> buildDeletedRows() {
        List<GameSet> deleted = gameSets.findByDeletedAtIsNotNullOrderByDeletedAtDescIdDesc();
        if (deleted.isEmpty()) {
            return List.of();
        }
        Set<Long> duelIds = deleted.stream().map(GameSet::duelId).collect(Collectors.toSet());
        Map<Long, Duel> duelById = duels.findAllById(duelIds).stream().collect(Collectors.toMap(Duel::id, d -> d));
        Set<Long> sessionIds = duelById.values().stream().map(Duel::sessionId).collect(Collectors.toSet());
        Map<Long, InhouseSession> sessionById = sessions.findAllById(sessionIds).stream()
                .collect(Collectors.toMap(InhouseSession::id, s -> s));

        Map<Long, List<ChainDuel>> chainsBySession = new HashMap<>();
        Map<Long, Long> sessionOfDuel = new HashMap<>();
        for (Duel d : duels.findBySessionIdIn(sessionIds)) {
            chainsBySession.computeIfAbsent(d.sessionId(), id -> new ArrayList<>()).add(chainDuel(d));
            sessionOfDuel.put(d.id(), d.sessionId());
        }
        Map<Long, List<SetPick>> validPicksBySession = new HashMap<>();
        for (SetPick pick : toPicks(gameSets.findActivePicksByDuelIds(sessionOfDuel.keySet()))) {
            validPicksBySession.computeIfAbsent(sessionOfDuel.get(pick.duelId()), id -> new ArrayList<>()).add(pick);
        }
        Map<Long, List<String>> deletedPicks = new HashMap<>();
        for (PickProjection p : gameSets.findPicksBySetIds(deleted.stream().map(GameSet::id).toList())) {
            deletedPicks.computeIfAbsent(p.getSetId(), id -> new ArrayList<>()).add(p.getChampionKey());
        }
        Map<String, Long> activeByFingerprint = new HashMap<>();
        gameSets.findByFingerprintInAndDeletedAtIsNull(
                deleted.stream().map(GameSet::fingerprint).collect(Collectors.toSet()))
                .forEach(g -> activeByFingerprint.put(g.fingerprint(), g.id()));
        Map<String, Long> activeByDuelNo = new HashMap<>();
        gameSets.findByDuelIdInAndDeletedAtIsNull(duelIds)
                .forEach(g -> activeByDuelNo.put(g.duelId() + ":" + g.setNo(), g.id()));

        List<DeletedSetRow> rows = new ArrayList<>();
        for (GameSet set : deleted) {
            Duel duel = duelById.get(set.duelId());
            InhouseSession session = sessionById.get(duel.sessionId());
            List<Conflict> conflicts = FearlessConflicts.conflicts(chainsBySession.get(duel.sessionId()), set.id(),
                    duel.id(), set.setNo(), deletedPicks.getOrDefault(set.id(), List.of()),
                    validPicksBySession.getOrDefault(duel.sessionId(), List.of()));
            rows.add(new DeletedSetRow(set.id(), duel.id(), duel.seq(), session.id(), session.startDate(),
                    set.setNo(), set.shotDate(), set.durationSec(), set.winnerTeam(), set.team1Side(),
                    set.team1Totals().kills(), set.team2Totals().kills(), set.deletedAt(),
                    new DeletedSetRow.Blockers(activeByFingerprint.get(set.fingerprint()),
                            activeByDuelNo.get(set.duelId() + ":" + set.setNo())),
                    conflicts));
        }
        return rows;
    }

    private Duel duelOf(long setId) {
        GameSet set = gameSets.findById(setId).orElseThrow(() -> setNotFound(setId));
        return duels.findById(set.duelId()).orElseThrow(() -> setNotFound(setId));
    }

    private GameSet lockAll(Duel duel, long setId) {
        sessions.findByIdForUpdate(duel.sessionId()).orElseThrow(() -> setNotFound(setId));
        duels.findByIdForUpdate(duel.id()).orElseThrow(() -> setNotFound(setId));
        return gameSets.findByIdForUpdate(setId).orElseThrow(() -> setNotFound(setId));
    }

    private RuntimeException translateRestore(DataIntegrityViolationException e, long setId) {
        String constraint = UniqueViolations.constraintName(e);
        if (constraint == null) {
            return e;
        }
        if (constraint.endsWith(FINGERPRINT_CONSTRAINT)) {
            return tx.execute(status -> gameSets.findById(setId)
                    .flatMap(set -> gameSets.findByFingerprintAndDeletedAtIsNull(set.fingerprint()))
                    .map(SetLifecycleService::fingerprintConflict)
                    .orElseGet(() -> ApiException.conflict(RecordEditErrorCodes.RESTORE_FINGERPRINT_CONFLICT,
                            "같은 경기가 이미 등록되어 있어 복구할 수 없습니다.")));
        }
        if (constraint.endsWith(SET_NO_CONSTRAINT)) {
            return tx.execute(status -> gameSets.findById(setId)
                    .flatMap(set -> gameSets.findByDuelIdAndDeletedAtIsNullOrderBySetNoAsc(set.duelId()).stream()
                            .filter(other -> other.setNo() == set.setNo()).findFirst())
                    .map(other -> setNoConflict(other.id()))
                    .orElseGet(() -> ApiException.conflict(RecordEditErrorCodes.RESTORE_SET_NO_CONFLICT,
                            "같은 번호의 세트가 이미 있어 복구할 수 없습니다.")));
        }
        return e;
    }

    private static ApiException fingerprintConflict(GameSet existing) {
        return ApiException.conflict(RecordEditErrorCodes.RESTORE_FINGERPRINT_CONFLICT,
                "같은 경기가 이미 등록되어 있어 복구할 수 없습니다.")
                .with("existingSetId", existing.id()).with("existingDuelId", existing.duelId())
                .with("existingSetNo", existing.setNo());
    }

    private static ApiException setNoConflict(long existingSetId) {
        return ApiException.conflict(RecordEditErrorCodes.RESTORE_SET_NO_CONFLICT,
                "같은 대결에 같은 번호의 세트가 이미 있어 복구할 수 없습니다.").with("existingSetId", existingSetId);
    }

    private static ApiException setNotFound(long id) {
        return ApiException.notFound(PublicReadErrorCodes.SET_NOT_FOUND, "세트를 찾을 수 없습니다: " + id);
    }

    private static List<String> warningsOf(List<Conflict> conflicts) {
        Map<Long, List<Conflict>> bySet = new LinkedHashMap<>();
        conflicts.forEach(c -> bySet.computeIfAbsent(c.setId(), id -> new ArrayList<>()).add(c));
        List<String> warnings = new ArrayList<>();
        bySet.values().forEach(group -> {
            Conflict first = group.get(0);
            warnings.add("대결 " + first.duelSeq() + "의 " + first.setNo() + "세트와 같은 챔피언을 씁니다: "
                    + group.stream().map(Conflict::championKey).collect(Collectors.joining(", ")));
        });
        return warnings;
    }

    private static ChainDuel chainDuel(Duel d) {
        return new ChainDuel(d.id(), d.seq(), d.fearlessMode() == FearlessMode.CONTINUE);
    }

    private static List<SetPick> toPicks(List<PickProjection> rows) {
        return rows.stream().map(r -> new SetPick(r.getSetId(), r.getDuelId(), r.getSetNo(), r.getChampionKey()))
                .toList();
    }
}
