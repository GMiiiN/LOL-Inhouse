package com.lolinhouse.record;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 역할: 공개 응답 JSON에 내부 값이 새지 않았는지 검사하는 공통 도구(작업자 B~D의 통합 테스트도 쓴다).
 * 객체 키를 모든 깊이(배열, 중첩 recentSets 포함)에서 확인하고 어디서 걸렸는지 경로를 알려 준다.
 */
public final class PublicJsonAssertions {

    /** 공개 응답에 있으면 안 되는 키. 실명은 displayName으로만 나가므로 realName 키도 금지다. */
    public static final Set<String> FORBIDDEN_KEYS = Set.of(
            "imageKey", "imageHash", "fingerprint", "topTeam", "screenRow", "aliases",
            "createdAt", "updatedAt", "deletedAt", "realName", "gameNickname", "flagged");

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private PublicJsonAssertions() {
    }

    /** 직렬화된 응답 본문 문자열을 검사한다. */
    public static void assertNoForbiddenKeysInJson(String json) {
        assertNoForbiddenKeys(MAPPER.readTree(json));
    }

    /** DTO 객체를 직렬화해서 검사한다. */
    public static void assertNoForbiddenKeys(Object dto) {
        assertNoForbiddenKeys(MAPPER.valueToTree(dto));
    }

    public static void assertNoForbiddenKeys(JsonNode root) {
        List<String> found = new ArrayList<>();
        walk(root, "$", found);
        assertThat(found).as("공개 응답에 있으면 안 되는 키").isEmpty();
    }

    private static void walk(JsonNode node, String path, List<String> found) {
        if (node.isObject()) {
            for (var entry : node.properties()) {
                String childPath = path + "." + entry.getKey();
                if (FORBIDDEN_KEYS.contains(entry.getKey())) {
                    found.add(childPath);
                }
                walk(entry.getValue(), childPath, found);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                walk(node.get(i), path + "[" + i + "]", found);
            }
        }
    }
}
