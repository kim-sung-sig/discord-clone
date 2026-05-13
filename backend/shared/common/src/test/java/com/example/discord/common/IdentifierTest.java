package com.example.discord.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentifierTest {
    @Test
    void createsIdentifierFromUuidString() {
        UUID value = UUID.randomUUID();

        Identifier identifier = Identifier.from(value.toString());

        assertThat(identifier.value()).isEqualTo(value);
        assertThat(identifier.toString()).isEqualTo(value.toString());
    }

    @Test
    void rejectsBlankIdentifier() {
        assertThatThrownBy(() -> Identifier.from(" "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("identifier must not be blank");
    }
}
