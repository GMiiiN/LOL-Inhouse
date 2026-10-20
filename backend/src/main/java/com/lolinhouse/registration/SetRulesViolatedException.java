package com.lolinhouse.registration;

import java.util.List;

/** 역할: 저장 명령이 도메인 규칙을 어겼다는 신호. RegistrationController가 422 SET_RULES_VIOLATED(+violations, warnings)로 바꾼다. */
public class SetRulesViolatedException extends RuntimeException {

    private final transient List<String> violations;
    private final transient List<String> warnings;

    public SetRulesViolatedException(List<String> violations, List<String> warnings) {
        super("세트 규칙 위반 " + violations.size() + "건");
        this.violations = List.copyOf(violations);
        this.warnings = List.copyOf(warnings);
    }

    public List<String> violations() {
        return violations;
    }

    public List<String> warnings() {
        return warnings;
    }
}
