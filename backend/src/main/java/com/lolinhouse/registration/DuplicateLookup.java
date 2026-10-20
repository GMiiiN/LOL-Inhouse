package com.lolinhouse.registration;

import java.util.Optional;

/** 역할: 지문으로 유효 세트를 찾는 함수. 평가기를 DB 없이 시험할 수 있게 분리했다. */
@FunctionalInterface
public interface DuplicateLookup {

    Optional<DuplicateSet> find(String fingerprint);
}
