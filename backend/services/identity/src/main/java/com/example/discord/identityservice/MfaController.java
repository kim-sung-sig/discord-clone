package com.example.discord.identityservice;

import com.example.discord.identity.AccessTokenService;
import com.example.discord.identity.AccessTokenClaims;
import com.example.discord.identity.TokenVerificationException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth/mfa/totp")
@ConditionalOnProperty(name = "discord.auth.mfa.enabled", havingValue = "true")
class MfaController {
    private static final Logger LOG = LoggerFactory.getLogger(MfaController.class);
    private final JdbcTotpStore store;
    private final AccessTokenService tokens;
    private final Clock clock;
    private final String origin;
    private final SecureRandom random = new SecureRandom();

    MfaController(JdbcTotpStore store, AccessTokenService tokens, Clock clock, @Value("${discord.auth.webauthn.origin:}") String origin) {
        this.store = store;
        this.tokens = tokens;
        this.clock = clock;
        this.origin = origin;
    }

    @PostMapping("/enroll")
    ResponseEntity<Map<String, String>> enroll(@RequestHeader(value = "Authorization", required = false) String header) {
        UUID owner = caller(header);
        byte[] secret = new byte[20];
        random.nextBytes(secret);
        try {
            if (!store.enroll(owner, secret, clock.instant().plusSeconds(300))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "MFA enrollment already exists");
            }
            return ResponseEntity.ok().header("Cache-Control", "no-store").body(Map.of("secret", base32(secret)));
        } finally {
            java.util.Arrays.fill(secret, (byte) 0);
        }
    }

    @PostMapping("/confirm")
    ResponseEntity<Void> confirm(@RequestHeader(value = "Authorization", required = false) String header, @RequestBody Code request) {
        if (!store.verify(caller(header), request.code(), clock.instant(), true)) {
            throw denied();
        }
        return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
    }

    @PostMapping("/unlock")
    ResponseEntity<Void> unlock(HttpServletRequest request) {
        UUID owner = passkeyCaller(request);
        try {
            store.unlock(owner);
            LOG.info("totp unlocked user={} result=204", owner);
            return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
        } catch (IllegalArgumentException exception) {
            LOG.warn("totp unlock rejected user={} result=409", owner);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "MFA account unavailable");
        } catch (IllegalStateException exception) {
            LOG.warn("totp unlock unavailable user={} result=503", owner);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "MFA storage unavailable");
        }
    }

    @PostMapping("/reset")
    ResponseEntity<Map<String, String>> reset(HttpServletRequest request) {
        UUID owner = passkeyCaller(request);
        byte[] secret = new byte[20];
        random.nextBytes(secret);
        try {
            store.reset(owner, secret, clock.instant().plusSeconds(300));
            LOG.info("totp reset pending user={} expiresInSeconds=300 result=200", owner);
            return ResponseEntity.ok().header("Cache-Control", "no-store").body(Map.of("secret", base32(secret)));
        } catch (IllegalArgumentException exception) {
            LOG.warn("totp reset rejected user={} result=409", owner);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "MFA account unavailable");
        } catch (IllegalStateException exception) {
            LOG.warn("totp reset unavailable user={} result=503", owner);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "MFA storage unavailable");
        } finally {
            java.util.Arrays.fill(secret, (byte) 0);
        }
    }

    @PostMapping("/challenges")
    Map<String, UUID> challenge(@RequestHeader(value = "Authorization", required = false) String header) {
        UUID owner = caller(header);
        try {
            return Map.of("challengeId", store.challenge(owner, clock.instant()));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "MFA challenge unavailable");
        }
    }

    @PostMapping("/challenges/{id}/complete")
    ResponseEntity<Map<String, String>> complete(@RequestHeader(value = "Authorization", required = false) String header,
            @PathVariable UUID id, @RequestBody Code request) {
        UUID owner = caller(header);
        if (!store.complete(owner, id, request.code(), clock.instant())) {
            throw denied();
        }
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(Map.of("accessToken", tokens.issueMfa(owner, "totp")));
    }

    private UUID caller(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw denied();
        }
        try {
            return tokens.verify(header.substring(7)).userId();
        } catch (TokenVerificationException | IllegalArgumentException exception) {
            throw denied();
        }
    }

    private UUID passkeyCaller(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) throw denied();
        AccessTokenClaims claims;
        try {
            claims = tokens.verify(header.substring(7));
        } catch (TokenVerificationException | IllegalArgumentException exception) {
            throw denied();
        }
        if (!claims.amr().equals(java.util.List.of("passkey")) || claims.authTime() == null
                || !claims.authTime().plusSeconds(300).isAfter(clock.instant())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Fresh passkey proof required");
        if (origin.isBlank() || !origin.equals(request.getHeader("Origin")) || !"127.0.0.1".equals(request.getRemoteAddr())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Local origin required");
        }
        return claims.userId();
    }

    private static ResponseStatusException denied() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "MFA authentication failed");
    }

    private static String base32(byte[] bytes) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        StringBuilder result = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 255);
            bits += 8;
            while (bits >= 5) {
                bits -= 5;
                result.append(alphabet.charAt((buffer >>> bits) & 31));
            }
        }
        if (bits > 0) result.append(alphabet.charAt((buffer << (5 - bits)) & 31));
        return result.toString();
    }

    record Code(String code) { }
}
