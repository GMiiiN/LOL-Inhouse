package com.lolinhouse.player;

import com.lolinhouse.domain.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlayerTest {

    @Test
    void unregisteredPlayerShowsGameNickname() {
        Player player = Player.unregistered("Moonlight_Fox");

        assertThat(player.isRegistered()).isFalse();
        assertThat(player.realName()).isNull();
        assertThat(player.displayName()).isEqualTo("Moonlight_Fox");
    }

    @Test
    void registeredPlayerShowsRealName() {
        Player player = Player.registered("Moonlight_Fox", "  김철수 ", Position.MID);

        assertThat(player.isRegistered()).isTrue();
        assertThat(player.displayName()).isEqualTo("김철수");
        assertThat(player.gameNickname()).isEqualTo("Moonlight_Fox");
        assertThat(player.mainPosition()).isEqualTo(Position.MID);
    }

    @Test
    void registeringAnUnregisteredPlayerChangesDisplayNameButKeepsNickname() {
        Player player = Player.unregistered("Fox");

        player.changeRealName("김철수");

        assertThat(player.isRegistered()).isTrue();
        assertThat(player.displayName()).isEqualTo("김철수");
        assertThat(player.gameNickname()).isEqualTo("Fox");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankRealNameIsRejected(String name) {
        Player player = Player.unregistered("Fox");

        assertThatThrownBy(() -> player.changeRealName(name)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Player.registered("Fox", name, null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(player.isRegistered()).isFalse();
    }

    @Test
    void nullRealNameCannotClearRegistration() {
        Player player = Player.registered("Fox", "김철수", null);

        assertThatThrownBy(() -> player.changeRealName(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(player.isRegistered()).isTrue();
    }

    @Test
    void realNameLongerThanFiftyIsRejected() {
        assertThatThrownBy(() -> Player.registered("Fox", "가".repeat(51), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mainPositionCanBeChangedAndCleared() {
        Player player = Player.registered("Fox", "김철수", Position.TOP);

        player.changeMainPosition(Position.SUPPORT);
        assertThat(player.mainPosition()).isEqualTo(Position.SUPPORT);

        player.changeMainPosition(null);
        assertThat(player.mainPosition()).isNull();
    }

    @Test
    void gameNicknameIsNormalizedOnCreateAndChange() {
        Player player = Player.unregistered(" Hide  On Bush ");
        assertThat(player.gameNickname()).isEqualTo("Hide On Bush");

        player.changeGameNickname("Ｆaker");
        assertThat(player.gameNickname()).isEqualTo("Faker");
    }

    @Test
    void blankGameNicknameIsRejected() {
        assertThatThrownBy(() -> Player.unregistered("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Player.unregistered(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Player.unregistered("Fox").changeGameNickname(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void displayNameFollowsGameNicknameChangeWhileUnregistered() {
        Player player = Player.unregistered("Fox");

        player.changeGameNickname("Cat");

        assertThat(player.displayName()).isEqualTo("Cat");
    }
}
