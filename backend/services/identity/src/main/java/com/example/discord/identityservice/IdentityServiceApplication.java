package com.example.discord.identityservice;

import com.example.discord.identity.AccessTokenService;
import com.example.discord.identity.PemLocationReader;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableConfigurationProperties(IdentityServiceApplication.JwtProperties.class)
public class IdentityServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(IdentityServiceApplication.class, args);
    }

    @Bean
    Clock authClock() {
        return Clock.systemUTC();
    }

    @Bean
    AccessTokenService accessTokenService(
        JwtProperties properties, Clock authClock
    ) {
        validate(properties);
        return new AccessTokenService(privateKey(properties.privateKeyLocation()), publicKeys(properties.publicKeyLocations()), properties.keyId(), properties.issuer(), properties.audience(), Duration.ofHours(1), authClock);
    }

    private static PrivateKey privateKey(String location) {
        try {
            String pem = PemLocationReader.readPrivateKey(location);
            String body = pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
            return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(body)));
        } catch (Exception exception) {
            throw new IllegalStateException("access token configuration invalid");
        }
    }
    private static Map<String, PublicKey> publicKeys(Map<String, String> locations) {
        try { Map<String, PublicKey> keys = new java.util.HashMap<>(); for (Map.Entry<String, String> entry : locations.entrySet()) { String pem = PemLocationReader.read(entry.getValue()); keys.put(entry.getKey(), KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pem.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "").replaceAll("\\s", ""))))); } return Map.copyOf(keys); } catch (Exception exception) { throw new IllegalStateException("access token configuration invalid"); }
    }

    private static void validate(JwtProperties properties) { if (properties == null || blank(properties.issuer()) || blank(properties.audience()) || blank(properties.keyId()) || blank(properties.privateKeyLocation()) || properties.publicKeyLocations() == null || properties.publicKeyLocations().isEmpty() || !properties.publicKeyLocations().containsKey(properties.keyId()) || properties.publicKeyLocations().entrySet().stream().anyMatch(entry -> blank(entry.getKey()) || blank(entry.getValue()))) throw new IllegalStateException("access token configuration invalid"); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }

    @ConfigurationProperties("discord.auth.jwt")
    record JwtProperties(String issuer, String audience, String keyId, String privateKeyLocation, Map<String, String> publicKeyLocations) {}
}
