package com.lolinhouse.common.web;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 역할: MySQL UNIQUE 위반(오류 1062) 예외에서 제약 이름을 꺼내 서비스가 409 코드로 구분하게 돕는다. */
public final class UniqueViolations {

    private static final Pattern CONSTRAINT_NAME = Pattern.compile("for key '([^']+)'");

    private UniqueViolations() {
    }

    /** 메시지의 "for key '...'"에서 제약 이름만 꺼낸다(없으면 null). 메시지에 닉네임 키가 섞일 수 있어 로그에 남기지 않는다. */
    public static String constraintName(Throwable error) {
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
}
