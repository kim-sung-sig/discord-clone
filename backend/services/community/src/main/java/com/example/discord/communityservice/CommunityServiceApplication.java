package com.example.discord.communityservice;

import com.example.discord.identity.BearerTokenVerifier;
import com.example.discord.identity.PemLocationReader;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableConfigurationProperties(CommunityServiceApplication.JwtProperties.class)
public class CommunityServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CommunityServiceApplication.class, args);
    }
    @Bean Clock authClock() { return Clock.systemUTC(); }
    @Bean BearerTokenVerifier bearerTokenVerifier(JwtProperties properties, Clock authClock) { validate(properties); return new BearerTokenVerifier(keys(properties.publicKeyLocations()), properties.issuer(), properties.audience(), authClock); }
    private static Map<String, PublicKey> keys(Map<String, String> values) {
        try { Map<String, PublicKey> keys = new java.util.HashMap<>(); for (Map.Entry<String, String> entry : values.entrySet()) { String pem = PemLocationReader.read(entry.getValue()); keys.put(entry.getKey(), KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pem.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "").replaceAll("\\s", ""))))); } return Map.copyOf(keys); }
        catch (Exception exception) { throw new IllegalStateException("access token configuration invalid"); }
    }
    private static void validate(JwtProperties properties) { if (properties == null || blank(properties.issuer()) || blank(properties.audience()) || blank(properties.keyId()) || properties.publicKeyLocations() == null || properties.publicKeyLocations().isEmpty() || !properties.publicKeyLocations().containsKey(properties.keyId()) || properties.publicKeyLocations().entrySet().stream().anyMatch(entry -> blank(entry.getKey()) || blank(entry.getValue()))) throw new IllegalStateException("access token configuration invalid"); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    @ConfigurationProperties("discord.auth.jwt") record JwtProperties(String issuer, String audience, String keyId, Map<String, String> publicKeyLocations) {}
}
