package com.example.discord.auth;

import com.example.discord.identity.BearerTokenVerifier;
import com.example.discord.identity.LoginFailureTracker;
import com.example.discord.identity.PasswordHasher;
import com.example.discord.identity.PemLocationReader;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AuthConfiguration.JwtProperties.class)
class AuthConfiguration {
    @Bean
    Clock authClock() {
        return Clock.systemUTC();
    }

    @Bean
    BearerTokenVerifier bearerTokenVerifier(JwtProperties properties, Clock authClock) {
        validatePublicKeys(properties);
        return new BearerTokenVerifier(publicKeys(properties.publicKeyLocations()), properties.issuer(), properties.audience(), authClock);
    }

    private static Map<String, PublicKey> publicKeys(Map<String, String> locations) {
        try {
            Map<String, PublicKey> keys = new HashMap<>();
            for (Map.Entry<String, String> entry : locations.entrySet()) {
                keys.put(entry.getKey(), KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(pem(PemLocationReader.read(entry.getValue())))));
            }
            return Map.copyOf(keys);
        } catch (Exception exception) {
            throw new IllegalStateException("access token configuration invalid");
        }
    }

    private static byte[] pem(String value) {
        return Base64.getMimeDecoder().decode(value.replaceAll("-----[^-]+-----|\\s", ""));
    }

    private static void validatePublicKeys(JwtProperties properties) {
        if (properties == null || blank(properties.issuer()) || blank(properties.audience()) || blank(properties.keyId())
            || properties.publicKeyLocations() == null || properties.publicKeyLocations().isEmpty()
            || !properties.publicKeyLocations().containsKey(properties.keyId())
            || properties.publicKeyLocations().entrySet().stream().anyMatch(entry -> blank(entry.getKey()) || blank(entry.getValue()))) {
            throw new IllegalStateException("access token configuration invalid");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    @Bean
    PasswordHasher passwordHasher() {
        return new BCryptPasswordHasher();
    }

    @Bean
    LoginFailureTracker loginFailureTracker(Clock authClock) {
        return new LoginFailureTracker(3, Duration.ofMinutes(15), authClock);
    }

    @ConfigurationProperties("discord.auth.jwt")
    record JwtProperties(String issuer, String audience, String keyId, Map<String, String> publicKeyLocations) {
    }
}
