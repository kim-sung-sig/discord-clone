package com.example.discord.auth;

import com.example.discord.identity.AccessTokenService;
import com.example.discord.identity.BCryptPasswordHasher;
import com.example.discord.identity.LoginFailureTracker;
import com.example.discord.identity.PasswordHasher;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class AuthConfiguration {
    @Bean
    Clock authClock() {
        return Clock.systemUTC();
    }

    @Bean
    AccessTokenService accessTokenService(
        @Value("${discord.auth.access-token-secret:local-development-auth-secret}") String accessTokenSecret,
        Clock authClock
    ) {
        return new AccessTokenService(accessTokenSecret, Duration.ofHours(1), authClock);
    }

    @Bean
    PasswordHasher passwordHasher() {
        return new BCryptPasswordHasher();
    }

    @Bean
    LoginFailureTracker loginFailureTracker(Clock authClock) {
        return new LoginFailureTracker(3, Duration.ofMinutes(15), authClock);
    }
}
