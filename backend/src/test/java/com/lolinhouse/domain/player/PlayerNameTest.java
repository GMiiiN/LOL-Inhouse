package com.lolinhouse.domain.player;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlayerNameTest {

    @Test
    void unregisteredPlayerShowsGameNickname() {
        PlayerName name = PlayerName.unregistered("Moonlight_Fox");

        assertThat(name.isRegistered()).isFalse();
        assertThat(name.displayName()).isEqualTo("Moonlight_Fox");
    }

    @Test
    void registeringSwitchesDisplayNameToRealName() {
        PlayerName registered = PlayerName.unregistered("Moonlight_Fox").register("김민수");

        assertThat(registered.isRegistered()).isTrue();
        assertThat(registered.displayName()).isEqualTo("김민수");
        assertThat(registered.gameNickname()).isEqualTo("Moonlight_Fox");
    }

    @Test
    void blankRealNameIsRejected() {
        assertThatThrownBy(() -> PlayerName.unregistered("Moonlight_Fox").register("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
