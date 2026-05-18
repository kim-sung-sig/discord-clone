package com.example.discord.voice;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;

@Configuration
class VoiceConfiguration {
    @Bean
    @Profile("!media-livekit")
    VoiceTokenSigner skeletonVoiceTokenSigner() {
        return new SkeletonLiveKitTokenSigner();
    }

    @Bean
    @Profile("media-livekit")
    VoiceTokenSigner liveKitVoiceTokenSigner(
        @Value("${discord.media.livekit.api-key}") String apiKey,
        @Value("${discord.media.livekit.api-secret}") String apiSecret
    ) {
        return new LiveKitVoiceTokenSigner(apiKey, apiSecret);
    }

    @Bean
    InMemoryVoiceService voiceService(VoiceTokenSigner voiceTokenSigner) {
        return new InMemoryVoiceService(Clock.systemUTC(), voiceTokenSigner);
    }
}
