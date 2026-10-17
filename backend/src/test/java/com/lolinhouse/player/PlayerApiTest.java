package com.lolinhouse.player;

import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.lolinhouse.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 역할: 선수·별칭 관리 API(설계 §5)의 상태 코드와 응답 모양을 MockMvc로 검증한다.
 * 인증은 @WithMockUser, 변경 요청은 csrf(). 테스트끼리 간섭하지 않도록 닉네임은 매번 고유 접미사를 붙인다.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(roles = "ADMIN")
class PlayerApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PlayerRepository playerRepository;

    @Autowired
    PlayerAliasRepository aliasRepository;

    static String unique(String base) {
        return base + UUID.randomUUID().toString().substring(0, 8);
    }

    static String json(String realName, String position, String... nicknames) {
        String names = String.join(",", java.util.Arrays.stream(nicknames).map(n -> "\"" + n + "\"").toList());
        return "{\"realName\":" + (realName == null ? "null" : "\"" + realName + "\"")
                + ",\"mainPosition\":" + (position == null ? "null" : "\"" + position + "\"")
                + ",\"nicknames\":[" + names + "]}";
    }

    ResultActions postJson(String url, String body) throws Exception {
        return mockMvc.perform(post(url).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** 선수를 만들고 응답 본문(JSON 문자열)을 돌려준다. */
    String createPlayer(String realName, String position, String... nicknames) throws Exception {
        return postJson("/api/admin/players", json(realName, position, nicknames))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    static int idOf(String body) {
        return JsonPath.read(body, "$.id");
    }

    static int aliasId(String body, int index) {
        return JsonPath.read(body, "$.aliases[" + index + "].id");
    }

    // ---- 인증 ----

    @Test
    @WithAnonymousUser
    void anonymousRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/players"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // ---- 생성 ----

    @Test
    void createReturns201WithLocationAndNormalizedAliases() throws Exception {
        String nick = unique("Hide on bush");
        postJson("/api/admin/players", json("홍길동", "MID", "  " + nick + "  ", unique("두번째")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/admin/players/\\d+")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.displayName").value("홍길동"))
                .andExpect(jsonPath("$.realName").value("홍길동"))
                .andExpect(jsonPath("$.registered").value(true))
                .andExpect(jsonPath("$.mainPosition").value("MID"))
                .andExpect(jsonPath("$.aliases.length()").value(2))
                .andExpect(jsonPath("$.aliases[0].nickname").value(nick));
    }

    @Test
    void createStoresFirstNicknameAsRepresentative() throws Exception {
        String body = createPlayer("대표확인", "TOP", unique("첫째"), unique("둘째"));
        Player stored = playerRepository.findById((long) idOf(body)).orElseThrow();
        assertThat(stored.gameNickname()).isEqualTo(JsonPath.read(body, "$.aliases[0].nickname"));
    }

    @Test
    void createWithoutMainPositionIsAllowed() throws Exception {
        postJson("/api/admin/players", json("포지션없음", null, unique("닉")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mainPosition").doesNotExist());
    }

    @Test
    void createWithBlankRealNameIsValidationFailedWithFieldErrors() throws Exception {
        postJson("/api/admin/players", json("  ", "MID", unique("닉")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("realName"))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    }

    @Test
    void createWithTooLongRealNameIsValidationFailed() throws Exception {
        postJson("/api/admin/players", json("가".repeat(51), "MID", unique("닉")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createWithNoNicknamesOrTooManyIsValidationFailed() throws Exception {
        postJson("/api/admin/players", json("빈목록", "MID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("nicknames"));
        String[] eleven = new String[11];
        for (int i = 0; i < eleven.length; i++) {
            eleven[i] = unique("n" + i);
        }
        postJson("/api/admin/players", json("열한개", "MID", eleven))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createWithTooLongOrBlankNicknameIsValidationFailed() throws Exception {
        postJson("/api/admin/players", json("긴닉", "MID", "a".repeat(51)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("nicknames[0]"));
        postJson("/api/admin/players", json("빈닉", "MID", "  "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createWithBrokenJsonOrUnknownPositionIsMalformedRequest() throws Exception {
        postJson("/api/admin/players", "{not json")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        postJson("/api/admin/players", json("포지션오류", "MIDDLE", unique("닉")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void duplicateNicknamesInRequestByComparisonKeyIsBadRequest() throws Exception {
        String base = unique("Dup");
        // 같은 글자의 대문자 / 전각 표기는 비교 키가 같다
        postJson("/api/admin/players", json("요청내중복", "ADC", base, base.toUpperCase()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_NICKNAME_IN_REQUEST"));
        postJson("/api/admin/players", json("요청내중복2", "ADC", "ＦｕｌｌＷｉｄｔｈ" + base.substring(3), "fullwidth" + base.substring(3)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_NICKNAME_IN_REQUEST"));
    }

    @Test
    void createConflictingWithExistingAliasIsConflictWithDetails() throws Exception {
        String base = unique("Taken");
        String owner = createPlayer("원래주인", "TOP", base);
        long ownerId = idOf(owner);
        // 대소문자만 다른 표기와 전각 표기 모두 같은 키라서 충돌한다
        postJson("/api/admin/players", json("새사람", "MID", unique("free"), base.toLowerCase()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ALIAS_CONFLICT"))
                .andExpect(jsonPath("$.conflicts.length()").value(1))
                .andExpect(jsonPath("$.conflicts[0].nickname").value(base.toLowerCase()))
                .andExpect(jsonPath("$.conflicts[0].playerId").value(ownerId))
                .andExpect(jsonPath("$.conflicts[0].playerDisplayName").value("원래주인"));
    }

    @Test
    void conflictedCreateLeavesNoPlayerBehind() throws Exception {
        String base = unique("Rollback");
        createPlayer("먼저", "TOP", base);
        long before = playerRepository.count();
        postJson("/api/admin/players", json("나중", "TOP", unique("ok"), base)).andExpect(status().isConflict());
        assertThat(playerRepository.count()).isEqualTo(before);
    }

    // ---- 조회 ----

    @Test
    void getAndListReturnPlayersWithAliases() throws Exception {
        String a = unique("목록A");
        String b = unique("목록B");
        String body = createPlayer("목록선수", "SUPPORT", a, b);
        long id = idOf(body);

        mockMvc.perform(get("/api/admin/players/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.aliases[0].nickname").value(a))
                .andExpect(jsonPath("$.aliases[1].nickname").value(b));

        String list = mockMvc.perform(get("/api/admin/players")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> nicknames = JsonPath.read(list, "$[?(@.id==" + id + ")].aliases[*].nickname");
        assertThat(nicknames).containsExactly(a, b);
    }

    @Test
    void getUnknownPlayerIs404() throws Exception {
        mockMvc.perform(get("/api/admin/players/999999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("PLAYER_NOT_FOUND"));
    }

    // ---- 수정 ----

    private long unregisteredPlayer(String nickname) {
        Player player = playerRepository.save(Player.unregistered(nickname));
        aliasRepository.save(PlayerAlias.of(player.id(), nickname));
        return player.id();
    }

    ResultActions putJson(long id, String body) throws Exception {
        return mockMvc.perform(put("/api/admin/players/" + id).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void putRegistersUnregisteredPlayerByGivingRealName() throws Exception {
        String nick = unique("미등록");
        long id = unregisteredPlayer(nick);
        mockMvc.perform(get("/api/admin/players/" + id))
                .andExpect(jsonPath("$.registered").value(false))
                .andExpect(jsonPath("$.displayName").value(nick))
                .andExpect(jsonPath("$.realName").doesNotExist());

        putJson(id, "{\"realName\":\" 김철수 \",\"mainPosition\":\"JUNGLE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered").value(true))
                .andExpect(jsonPath("$.displayName").value("김철수"))
                .andExpect(jsonPath("$.mainPosition").value("JUNGLE"))
                .andExpect(jsonPath("$.aliases.length()").value(1));
    }

    @Test
    void putWithoutRealNameKeepsUnregisteredPlayerUnregistered() throws Exception {
        long id = unregisteredPlayer(unique("계속미등록"));
        putJson(id, "{\"realName\":null,\"mainPosition\":\"TOP\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered").value(false))
                .andExpect(jsonPath("$.mainPosition").value("TOP"));
    }

    @Test
    void putChangesRealNameAndPositionOfRegisteredPlayer() throws Exception {
        long id = idOf(createPlayer("바꾸기전", "TOP", unique("닉")));
        putJson(id, "{\"realName\":\"바꾼후\",\"mainPosition\":\"ADC\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("바꾼후"))
                .andExpect(jsonPath("$.mainPosition").value("ADC"));
    }

    @Test
    void putClearingRealNameOfRegisteredPlayerIsBadRequest() throws Exception {
        long id = idOf(createPlayer("지울수없음", "TOP", unique("닉")));
        for (String body : List.of("{\"mainPosition\":\"TOP\"}", "{\"realName\":null}", "{\"realName\":\"  \"}")) {
            putJson(id, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("REAL_NAME_CANNOT_BE_CLEARED"));
        }
        mockMvc.perform(get("/api/admin/players/" + id)).andExpect(jsonPath("$.realName").value("지울수없음"));
    }

    @Test
    void putTooLongRealNameIsValidationFailedAndUnknownPlayerIs404() throws Exception {
        long id = idOf(createPlayer("길이확인", "TOP", unique("닉")));
        putJson(id, "{\"realName\":\"" + "가".repeat(51) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        putJson(999999999L, "{\"realName\":\"없는선수\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAYER_NOT_FOUND"));
    }

    // ---- 별칭 추가 ----

    ResultActions addAlias(long playerId, String nickname) throws Exception {
        return postJson("/api/admin/players/" + playerId + "/aliases", "{\"nickname\":\"" + nickname + "\"}");
    }

    @Test
    void addAliasReturns201WithUpdatedPlayer() throws Exception {
        long id = idOf(createPlayer("별칭추가", "MID", unique("기존")));
        String added = unique("추가");
        addAlias(id, added)
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.aliases.length()").value(2))
                .andExpect(jsonPath("$.aliases[1].nickname").value(added));
    }

    @Test
    void addAliasToUnknownPlayerIs404() throws Exception {
        addAlias(999999999L, unique("없는선수"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAYER_NOT_FOUND"));
    }

    @Test
    void addAliasWithBlankNicknameIsValidationFailed() throws Exception {
        long id = idOf(createPlayer("빈별칭", "MID", unique("닉")));
        addAlias(id, " ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void addAliasWithKeyOfAnotherPlayersAliasIsConflict() throws Exception {
        String base = unique("Owned");
        long owner = idOf(createPlayer("주인", "TOP", base));
        long other = idOf(createPlayer("다른사람", "MID", unique("닉")));
        // 대소문자 차이
        addAlias(other, base.toUpperCase())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALIAS_CONFLICT"))
                .andExpect(jsonPath("$.conflicts[0].playerId").value(owner))
                .andExpect(jsonPath("$.conflicts[0].playerDisplayName").value("주인"));
        // 전각 표기: 영문자를 전각으로 바꾼 같은 닉네임
        String fullWidth = base.chars().mapToObj(c -> c < 128 && c > 32 ? String.valueOf((char) (c + 0xFEE0)) : String.valueOf((char) c))
                .collect(java.util.stream.Collectors.joining());
        addAlias(other, fullWidth)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALIAS_CONFLICT"));
        // 같은 선수에게 다시 추가해도 충돌
        addAlias(owner, base)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALIAS_CONFLICT"));
    }

    // ---- 별칭 삭제 ----

    ResultActions deleteAlias(long playerId, long aliasId) throws Exception {
        return mockMvc.perform(delete("/api/admin/players/" + playerId + "/aliases/" + aliasId).with(csrf()));
    }

    @Test
    void deleteAliasReturns204AndRemovesIt() throws Exception {
        String body = createPlayer("삭제", "MID", unique("하나"), unique("둘"));
        long id = idOf(body);
        deleteAlias(id, aliasId(body, 1)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/admin/players/" + id)).andExpect(jsonPath("$.aliases.length()").value(1));
    }

    @Test
    void deleteLastAliasIsConflictLastAlias() throws Exception {
        String body = createPlayer("마지막", "MID", unique("하나"));
        deleteAlias(idOf(body), aliasId(body, 0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ALIAS"));
    }

    @Test
    void deleteAliasOfUnknownPlayerOrWrongPlayerIs404() throws Exception {
        String first = createPlayer("선수1", "MID", unique("닉"));
        String second = createPlayer("선수2", "MID", unique("닉"));
        deleteAlias(999999999L, aliasId(first, 0))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAYER_NOT_FOUND"));
        deleteAlias(idOf(first), 999999999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ALIAS_NOT_FOUND"));
        // 다른 선수의 별칭 id로는 지울 수 없다
        deleteAlias(idOf(first), aliasId(second, 0))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ALIAS_NOT_FOUND"));
    }

    @Test
    void deletingRepresentativeAliasMovesRepresentativeToOldestRemaining() throws Exception {
        String a = unique("대표A");
        String b = unique("대표B");
        String c = unique("대표C");
        String body = createPlayer("대표갱신", "MID", a, b, c);
        long id = idOf(body);
        assertThat(playerRepository.findById(id).orElseThrow().gameNickname()).isEqualTo(a);

        // 대표가 아닌 별칭을 지우면 대표는 그대로
        deleteAlias(id, aliasId(body, 2)).andExpect(status().isNoContent());
        assertThat(playerRepository.findById(id).orElseThrow().gameNickname()).isEqualTo(a);

        // 대표(A)를 지우면 남은 것 중 가장 오래된 B
        deleteAlias(id, aliasId(body, 0)).andExpect(status().isNoContent());
        assertThat(playerRepository.findById(id).orElseThrow().gameNickname()).isEqualTo(b);
    }
}
