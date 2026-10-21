package com.lolinhouse;

import java.util.random.RandomGenerator;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 역할: 통합 테스트(@IntegrationTest)에서 RandomGenerator를 고정 값 빈으로 바꾼다. nextBoolean()은 항상 false라
 * 팀 짜기 결과가 좌우 교체 없이 나온다. 교체가 필요한 테스트는 FixedRandom.setBooleans(...)로 순서를 정하고 끝나면 reset()한다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestRandomConfiguration {

    @Bean
    @Primary
    FixedRandom fixedRandom() {
        return new FixedRandom();
    }

    /** 미리 정한 boolean을 차례로 돌려주고, 다 쓰면 false를 돌려준다. */
    public static class FixedRandom implements RandomGenerator {

        private final java.util.ArrayDeque<Boolean> booleans = new java.util.ArrayDeque<>();

        public synchronized void setBooleans(boolean... values) {
            booleans.clear();
            for (boolean value : values) {
                booleans.add(value);
            }
        }

        public synchronized void reset() {
            booleans.clear();
        }

        @Override
        public synchronized boolean nextBoolean() {
            Boolean next = booleans.poll();
            return next != null && next;
        }

        @Override
        public long nextLong() {
            return 0L;
        }
    }
}
