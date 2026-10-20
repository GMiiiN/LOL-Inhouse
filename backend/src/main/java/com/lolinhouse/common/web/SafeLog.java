package com.lolinhouse.common.web;

import org.hibernate.exception.ConstraintViolationException;

/**
 * 역할: 예외를 로그 한 줄로 바꾸되 getMessage()는 절대 쓰지 않는다. 예외 메시지에는 닉네임·응답 원문·SQL 값이 섞일 수 있어서다
 * (예: MySQL 중복 오류의 "Duplicate entry '닉네임' for key ..."). 남기는 것: 원인 사슬의 예외 클래스 이름, DB 제약 이름,
 * 가장 바깥 예외의 스택 프레임(메시지 없이 직접 포맷).
 * 설명: docs/design/m4-design.md §11.2-9
 */
public final class SafeLog {

    private static final int MAX_CHAIN = 5;
    private static final int MAX_FRAMES = 8;

    private SafeLog() {
    }

    public static String describe(Throwable throwable) {
        StringBuilder text = new StringBuilder();
        Throwable current = throwable;
        for (int depth = 0; current != null && depth < MAX_CHAIN; depth++) {
            text.append(depth == 0 ? "" : " <- ").append(current.getClass().getName());
            if (current instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                text.append("[constraint=").append(violation.getConstraintName()).append(']');
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        StackTraceElement[] frames = throwable.getStackTrace();
        for (int i = 0; i < Math.min(frames.length, MAX_FRAMES); i++) {
            text.append("\n\tat ").append(frames[i]);
        }
        return text.toString();
    }
}
