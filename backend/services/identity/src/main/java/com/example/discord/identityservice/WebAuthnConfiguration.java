package com.example.discord.identityservice;

import java.net.URI;
import java.time.Duration;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.webauthn.api.AuthenticatorSelectionCriteria;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;
import org.springframework.security.web.webauthn.api.ResidentKeyRequirement;
import org.springframework.security.web.webauthn.api.UserVerificationRequirement;
import org.springframework.security.web.webauthn.management.JdbcPublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.JdbcUserCredentialRepository;
import org.springframework.security.web.webauthn.management.Webauthn4JRelyingPartyOperations;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "discord.auth.webauthn.enabled", havingValue = "true")
class WebAuthnConfiguration {
    @Bean
    JdbcPublicKeyCredentialUserEntityRepository webAuthnUsers(DataSource mfaDataSource) {
        return new JdbcPublicKeyCredentialUserEntityRepository(new JdbcTemplate(mfaDataSource));
    }

    @Bean
    JdbcUserCredentialRepository webAuthnCredentials(DataSource mfaDataSource) {
        return new JdbcUserCredentialRepository(new JdbcTemplate(mfaDataSource));
    }

    @Bean
    Webauthn4JRelyingPartyOperations webAuthnOperations(Environment environment,
            JdbcPublicKeyCredentialUserEntityRepository users, JdbcUserCredentialRepository credentials) {
        String origin = environment.getRequiredProperty("discord.auth.webauthn.origin");
        URI uri = URI.create(origin);
        if (!"127.0.0.1".equals(environment.getProperty("server.address"))
                || !"http".equals(uri.getScheme()) || !"localhost".equals(uri.getHost())
                || uri.getPort() < 1 || uri.getPort() > 65535
                || !origin.equals("http://localhost:" + uri.getPort())) {
            throw new IllegalStateException("WebAuthn demo requires loopback binding and an exact localhost origin");
        }
        var operations = new Webauthn4JRelyingPartyOperations(users, credentials,
            PublicKeyCredentialRpEntity.builder().id("localhost").name("Discord local MFA").build(), Set.of(origin));
        operations.setCustomizeCreationOptions(builder -> builder.timeout(Duration.ofMinutes(3))
            .authenticatorSelection(AuthenticatorSelectionCriteria.builder()
                .residentKey(ResidentKeyRequirement.REQUIRED).userVerification(UserVerificationRequirement.REQUIRED).build()));
        operations.setCustomizeRequestOptions(builder -> builder.timeout(Duration.ofMinutes(3))
            .userVerification(UserVerificationRequirement.REQUIRED));
        return operations;
    }
}
