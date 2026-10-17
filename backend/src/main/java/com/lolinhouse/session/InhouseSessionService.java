package com.lolinhouse.session;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.lolinhouse.common.web.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 역할: 내전 묶음(하루)과 대결("대결 N") 생성·조회 규칙. 묶음 날짜 중복은 409 SESSION_DATE_CONFLICT(+기존 묶음 id),
 * 대결 번호는 max+1이고 동시 요청 충돌은 409 DUEL_SEQ_CONFLICT. 초기화 = 같은 묶음에 새 대결 생성(설계 D5).
 */
@Service
public class InhouseSessionService {

    private final InhouseSessionRepository sessionRepository;
    private final DuelRepository duelRepository;
    private final TransactionTemplate transactionTemplate;

    public InhouseSessionService(InhouseSessionRepository sessionRepository, DuelRepository duelRepository,
            TransactionTemplate transactionTemplate) {
        this.sessionRepository = sessionRepository;
        this.duelRepository = duelRepository;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 일부러 @Transactional이 아니다: 동시 요청의 UNIQUE 위반이 나면 그 트랜잭션은 이미 망가졌으므로,
     * 저장은 TransactionTemplate 안에서 끝내고 "기존 묶음 id 조회"는 새 트랜잭션에서 한다.
     */
    public InhouseSessionSummaryResponse create(LocalDate startDate) {
        throwIfDateTaken(startDate);
        try {
            InhouseSession saved = transactionTemplate.execute(status ->
                    sessionRepository.saveAndFlush(InhouseSession.of(startDate)));
            return new InhouseSessionSummaryResponse(saved.id(), saved.startDate(), 0);
        } catch (DataIntegrityViolationException e) {
            throwIfDateTaken(startDate); // 사전 조회 이후 다른 요청이 먼저 만든 경우
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<InhouseSessionSummaryResponse> list() {
        List<InhouseSession> sessions = sessionRepository.findAllByOrderByStartDateDesc();
        if (sessions.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> counts = duelRepository.countBySessionIds(sessions.stream().map(InhouseSession::id).toList())
                .stream()
                .collect(Collectors.toMap(DuelRepository.SessionDuelCount::getSessionId,
                        DuelRepository.SessionDuelCount::getDuelCount));
        return sessions.stream()
                .map(s -> new InhouseSessionSummaryResponse(s.id(), s.startDate(), counts.getOrDefault(s.id(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public InhouseSessionDetailResponse get(Long id) {
        InhouseSession session = sessionRepository.findById(id).orElseThrow(() -> sessionNotFound(id));
        List<DuelResponse> duels = duelRepository.findBySessionIdOrderBySeqAsc(id).stream()
                .map(DuelResponse::from).toList();
        return new InhouseSessionDetailResponse(session.id(), session.startDate(), duels);
    }

    @Transactional
    public DuelResponse createDuel(Long sessionId) {
        if (!sessionRepository.existsById(sessionId)) {
            throw sessionNotFound(sessionId);
        }
        int nextSeq = duelRepository.findMaxSeq(sessionId) + 1;
        try {
            return DuelResponse.from(duelRepository.saveAndFlush(Duel.of(sessionId, nextSeq)));
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 번호를 계산한 다른 요청이 먼저 저장됨 → DB UNIQUE (session_id, seq)가 막았다.
            throw ApiException.conflict("DUEL_SEQ_CONFLICT", "다른 요청이 같은 번호의 대결을 먼저 만들었습니다. 다시 시도하세요.");
        }
    }

    @Transactional(readOnly = true)
    public DuelResponse getDuel(Long id) {
        return duelRepository.findById(id).map(DuelResponse::from)
                .orElseThrow(() -> ApiException.notFound("DUEL_NOT_FOUND", "대결을 찾을 수 없습니다: " + id));
    }

    private void throwIfDateTaken(LocalDate startDate) {
        sessionRepository.findByStartDate(startDate).ifPresent(existing -> {
            throw ApiException.conflict("SESSION_DATE_CONFLICT", "이미 그 날짜의 내전 묶음이 있습니다: " + startDate)
                    .with("existingSessionId", existing.id());
        });
    }

    private ApiException sessionNotFound(Long id) {
        return ApiException.notFound("SESSION_NOT_FOUND", "내전 묶음을 찾을 수 없습니다: " + id);
    }
}
