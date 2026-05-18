package com.example.discord.voice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class VoiceConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(VoiceConfiguration.class);

    @Test
    void defaultProfileUsesSkeletonLiveKitTokenSigner() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(VoiceTokenSigner.class);
            assertThat(context.getBean(VoiceTokenSigner.class)).isInstanceOf(SkeletonLiveKitTokenSigner.class);
        });
    }

    @Test
    void mediaLiveKitProfileUsesSecretBackedLiveKitTokenSigner() {
        contextRunner
            .withPropertyValues(
                "spring.profiles.active=media-livekit",
                "discord.media.livekit.api-key=lk-prod-key",
                "discord.media.livekit.api-secret=livekit-production-secret-value-32",
                "discord.media.livekit.url=wss://livekit.example.com"
            )
            .run(context -> {
                assertThat(context).hasSingleBean(VoiceTokenSigner.class);
                assertThat(context.getBean(VoiceTokenSigner.class)).isInstanceOf(LiveKitVoiceTokenSigner.class);
            });
    }
}
