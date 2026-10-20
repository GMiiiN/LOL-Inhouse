package com.lolinhouse.ingest.extract;

import java.util.List;

import com.lolinhouse.ingest.job.ExtractionErrorCode;

/**
 * 역할: 추출 실패를 나르는 검사 예외. 작업의 error_code가 될 {@link ExtractionErrorCode}, 원인 HTTP 상태(없을 수 있음),
 * 재시도할 만한 일시 오류인지, 그때까지의 시도 기록을 담는다.
 * 메시지에는 Gemini 응답 원문·닉네임·키를 넣지 않는다(고정 문구와 코드만).
 * 설명: docs/design/m4-design.md §2.1, §11.2-9
 */
public class ExtractionException extends Exception {

    private final ExtractionErrorCode errorCode;
    private final Integer httpStatus;
    private final boolean transientError;
    private final List<Attempt> attempts;

    public ExtractionException(ExtractionErrorCode errorCode, Integer httpStatus, boolean transientError,
            String message) {
        this(errorCode, httpStatus, transientError, message, List.of());
    }

    /** HTTP 상태가 없고 재시도 대상도 아닌 실패(검증 실패, 설정 없음 등). */
    public ExtractionException(ExtractionErrorCode errorCode, String message) {
        this(errorCode, null, false, message, List.of());
    }

    private ExtractionException(ExtractionErrorCode errorCode, Integer httpStatus, boolean transientError,
            String message, List<Attempt> attempts) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.transientError = transientError;
        this.attempts = List.copyOf(attempts);
    }

    /** 같은 내용에 시도 기록을 붙인 새 예외. RetryingExtractor가 마지막 예외에 붙여 던진다. */
    public ExtractionException withAttempts(List<Attempt> attempts) {
        ExtractionException copy = new ExtractionException(errorCode, httpStatus, transientError, getMessage(), attempts);
        copy.setStackTrace(getStackTrace());
        return copy;
    }

    public ExtractionErrorCode errorCode() {
        return errorCode;
    }

    /** 실패 원인 HTTP 상태. 응답이 없었으면 null. */
    public Integer httpStatus() {
        return httpStatus;
    }

    /** 408·429·5xx·요청 시간 초과처럼 다시 시도해 볼 만한 오류인가. */
    public boolean transientError() {
        return transientError;
    }

    /** 실패하기까지의 시도 기록(없으면 빈 목록). attempt_count = size(). */
    public List<Attempt> attempts() {
        return attempts;
    }
}
