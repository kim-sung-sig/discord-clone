package com.example.discord.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

class MessageConfigurationTest {
    @Test
    void inMemoryMessageServiceIsRestrictedToTestProfile() throws Exception {
        Method factory = MessageConfiguration.class.getDeclaredMethod("inMemoryMessageService");

        assertThat(factory.getAnnotation(Profile.class).value()).containsExactly("test");
    }
}
