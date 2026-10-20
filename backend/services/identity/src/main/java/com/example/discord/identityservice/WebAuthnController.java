package com.example.discord.identityservice;

import com.example.discord.identity.AccessTokenClaims;
import com.example.discord.identity.AccessTokenService;
import com.example.discord.identity.TokenVerificationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.webauthn.api.AuthenticatorAssertionResponse;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.PublicKeyCredential;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;
import org.springframework.security.web.webauthn.jackson.WebauthnJacksonModule;
import org.springframework.security.web.webauthn.management.ImmutablePublicKeyCredentialCreationOptionsRequest;
import org.springframework.security.web.webauthn.management.ImmutablePublicKeyCredentialRequestOptionsRequest;
import org.springframework.security.web.webauthn.management.ImmutableRelyingPartyRegistrationRequest;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.RelyingPartyAuthenticationRequest;
import org.springframework.security.web.webauthn.management.RelyingPartyPublicKey;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/auth/mfa/webauthn")
@ConditionalOnProperty(name = "discord.auth.webauthn.enabled", havingValue = "true")
class WebAuthnController {
    private static final String PENDING = WebAuthnController.class.getName();
    private static final Logger LOG = LoggerFactory.getLogger(WebAuthnController.class);
    private final AccessTokenService tokens;
    private final Clock clock;
    private final WebAuthnRelyingPartyOperations operations;
    private final PublicKeyCredentialUserEntityRepository users;
    private final UserCredentialRepository credentials;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final String origin;
    private final JsonMapper json = JsonMapper.builder().addModule(new WebauthnJacksonModule()).build();

    WebAuthnController(AccessTokenService tokens, Clock clock, WebAuthnRelyingPartyOperations operations,
            PublicKeyCredentialUserEntityRepository users, UserCredentialRepository credentials,
            DataSource dataSource, @Value("${discord.auth.webauthn.origin}") String origin) {
        this.tokens = tokens;
        this.clock = clock;
        this.operations = operations;
        this.users = users;
        this.credentials = credentials;
        this.jdbc = new JdbcTemplate(dataSource);
        this.transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        this.origin = origin;
    }

    @PostMapping("/register/options")
    ResponseEntity<String> registerOptions(HttpServletRequest request) {
        return options(request, true);
    }

    @PostMapping("/authenticate/options")
    ResponseEntity<String> authenticateOptions(HttpServletRequest request) {
        return options(request, false);
    }

    private ResponseEntity<String> options(HttpServletRequest request, boolean registration) {
        UUID owner = caller(request, registration).userId();
        Object options = locked(owner, () -> {
                Long previous = jdbc.queryForObject("SELECT webauthn_started_at FROM mfa_totp WHERE user_id = ?", Long.class, owner.toString());
                if (previous != null && clock.instant().getEpochSecond() < previous + 10) {
                    throw failure(HttpStatus.TOO_MANY_REQUESTS);
                }
                var authentication = UsernamePasswordAuthenticationToken.authenticated(owner.toString(), null, List.of());
                Object result;
                if (registration) {
                    requireCapacity(owner);
                    result = operations.createPublicKeyCredentialCreationOptions(new ImmutablePublicKeyCredentialCreationOptionsRequest(authentication));
                } else {
                    var assertion = operations.createCredentialRequestOptions(new ImmutablePublicKeyCredentialRequestOptionsRequest(authentication));
                    if (assertion.getAllowCredentials().isEmpty()) throw failure(HttpStatus.CONFLICT);
                    result = assertion;
                }
                jdbc.update("UPDATE mfa_totp SET webauthn_started_at = ? WHERE user_id = ?", clock.instant().getEpochSecond(), owner.toString());
                return result;
        });
        HttpSession session = request.getSession(true);
        // ponytail: session-local atomicity is single-JVM only; redesign before distributed sessions.
        synchronized (session) {
            session.setMaxInactiveInterval(180);
            session.setAttribute(PENDING, new Pending(owner, registration, clock.instant().plusSeconds(180), options));
            return response(options);
        }
    }

    @PostMapping(value = "/register/complete", consumes = "application/json")
    ResponseEntity<Void> register(HttpServletRequest request) throws IOException {
        UUID owner = caller(request, true).userId();
        Pending pending = consume(request, owner, true);
        RelyingPartyPublicKey publicKey = read(request, new TypeReference<>() { });
        if (publicKey == null || publicKey.getLabel() == null || publicKey.getLabel().isBlank() || publicKey.getLabel().length() > 64) {
            throw failure(HttpStatus.BAD_REQUEST);
        }
        validateCredential(publicKey.getCredential());
        locked(owner, () -> {
            requireCapacity(owner);
            nativeVerify(() -> operations.registerCredential(new ImmutableRelyingPartyRegistrationRequest(
                (PublicKeyCredentialCreationOptions) pending.options(), publicKey)));
            return null;
        });
        return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
    }

    @PostMapping(value = "/authenticate/complete", consumes = "application/json")
    ResponseEntity<String> authenticate(HttpServletRequest request) throws IOException {
        UUID owner = caller(request, false).userId();
        Pending pending = consume(request, owner, false);
        PublicKeyCredential<AuthenticatorAssertionResponse> credential = read(request, new TypeReference<>() { });
        validateCredential(credential);
        return locked(owner, () -> {
            var verified = nativeVerify(() -> operations.authenticate(new RelyingPartyAuthenticationRequest(
                (PublicKeyCredentialRequestOptions) pending.options(), credential)));
            if (!owner.toString().equals(verified.getName())
                    || (credential.getResponse().getUserHandle() != null
                    && !verified.getId().equals(credential.getResponse().getUserHandle()))) {
                throw failure(HttpStatus.UNAUTHORIZED);
            }
            return response(Map.of("accessToken", tokens.issueMfa(owner, "passkey")));
        });
    }

    /** Account-bound read: the caller's own passkeys without key material. Same-origin GETs carry no Origin header. */
    @GetMapping("/credentials")
    ResponseEntity<String> listCredentials(HttpServletRequest request) {
        UUID owner = caller(request, false, false).userId();
        try {
            var user = users.findByUsername(owner.toString());
            List<Map<String, Object>> entries = user == null ? List.of() : credentials.findByUserId(user.getId()).stream()
                .sorted(Comparator.comparing(CredentialRecord::getCreated, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(WebAuthnController::summary).toList();
            LOG.info("webauthn credentials listed user={} count={}", owner, entries.size());
            return response(entries);
        } catch (DataAccessException exception) {
            LOG.warn("webauthn credential listing unavailable user={} result=503", owner);
            throw failure(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    /** Fresh MFA proof, exact Origin and ownership are required; unknown and foreign ids are both 404. */
    @DeleteMapping("/credentials/{id}")
    ResponseEntity<Void> deleteCredential(HttpServletRequest request, @PathVariable String id) {
        UUID owner = caller(request, true).userId();
        Bytes credentialId = credentialId(id);
        locked(owner, () -> {
            var user = users.findByUsername(owner.toString());
            CredentialRecord record = user == null ? null : credentials.findByCredentialId(credentialId);
            if (record == null || !user.getId().equals(record.getUserEntityUserId())) throw failure(HttpStatus.NOT_FOUND);
            int remaining = credentials.findByUserId(user.getId()).size();
            requireDeletable(owner, remaining);
            credentials.delete(credentialId);
            LOG.info("webauthn credential deleted user={} credentialId={} remaining={}", owner, credentialId.toBase64UrlString(), remaining - 1);
            return null;
        });
        return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
    }

    /** Last passkey deletion requires an active TOTP factor with fewer than five failures (approved policy Q2). */
    private void requireDeletable(UUID owner, int remaining) {
        if (remaining > 1) return;
        Integer failures = jdbc.queryForObject("SELECT failures FROM mfa_totp WHERE user_id = ?", Integer.class, owner.toString());
        if (failures == null || failures >= 5) throw failure(HttpStatus.CONFLICT);
    }

    private static Map<String, Object> summary(CredentialRecord record) {
        var entry = new LinkedHashMap<String, Object>();
        entry.put("id", record.getCredentialId().toBase64UrlString());
        entry.put("label", record.getLabel());
        entry.put("createdAt", Objects.toString(record.getCreated(), null));
        entry.put("lastUsedAt", Objects.toString(record.getLastUsed(), null));
        return entry;
    }

    private static Bytes credentialId(String id) {
        if (id == null || id.isEmpty() || id.length() > 1_000) throw failure(HttpStatus.BAD_REQUEST);
        try {
            Bytes bytes = Bytes.fromBase64(id);
            if (bytes.getBytes().length == 0 || bytes.getBytes().length > 750) throw failure(HttpStatus.BAD_REQUEST);
            return bytes;
        } catch (IllegalArgumentException exception) {
            throw failure(HttpStatus.BAD_REQUEST);
        }
    }

    private AccessTokenClaims caller(HttpServletRequest request, boolean freshMfa) {
        return caller(request, freshMfa, true);
    }

    private AccessTokenClaims caller(HttpServletRequest request, boolean freshMfa, boolean requireOrigin) {
        String bearer = request.getHeader("Authorization");
        if (bearer == null || !bearer.startsWith("Bearer ")) throw failure(HttpStatus.UNAUTHORIZED);
        AccessTokenClaims claims;
        try {
            claims = tokens.verify(bearer.substring(7));
        } catch (TokenVerificationException | IllegalArgumentException exception) {
            throw failure(HttpStatus.UNAUTHORIZED);
        }
        if ((requireOrigin && !origin.equals(request.getHeader("Origin"))) || !"127.0.0.1".equals(request.getRemoteAddr())) {
            throw failure(HttpStatus.FORBIDDEN);
        }
        if (freshMfa && (claims.amr().isEmpty() || claims.authTime() == null
                || !claims.authTime().plusSeconds(300).isAfter(clock.instant()))) {
            throw failure(HttpStatus.FORBIDDEN);
        }
        return claims;
    }

    private Pending consume(HttpServletRequest request, UUID owner, boolean registration) {
        HttpSession session = request.getSession(false);
        if (session == null) throw failure(HttpStatus.UNAUTHORIZED);
        synchronized (session) {
            Object value = session.getAttribute(PENDING);
            if (!(value instanceof Pending pending) || !pending.owner().equals(owner)) throw failure(HttpStatus.UNAUTHORIZED);
            session.removeAttribute(PENDING);
            if (pending.registration() != registration || !pending.expiresAt().isAfter(clock.instant())) throw failure(HttpStatus.UNAUTHORIZED);
            return pending;
        }
    }

    private <T> T locked(UUID owner, Supplier<T> action) {
        try {
            return transactions.execute(status -> {
                var active = jdbc.query("SELECT active FROM mfa_totp WHERE user_id = ? FOR UPDATE", (row, index) -> row.getBoolean(1), owner.toString());
                if (active.isEmpty() || !active.getFirst()) throw failure(HttpStatus.CONFLICT);
                return action.get();
            });
        } catch (DataAccessException exception) {
            throw failure(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private void requireCapacity(UUID owner) {
        var user = users.findByUsername(owner.toString());
        if (user != null && credentials.findByUserId(user.getId()).size() >= 5) throw failure(HttpStatus.CONFLICT);
    }

    private <T> T read(HttpServletRequest request, TypeReference<T> type) throws IOException {
        byte[] body = request.getInputStream().readNBytes(65_537);
        if (body.length > 65_536) throw failure(HttpStatus.PAYLOAD_TOO_LARGE);
        try {
            return json.readValue(body, type);
        } catch (RuntimeException exception) {
            throw failure(HttpStatus.BAD_REQUEST);
        }
    }

    private static void validateCredential(PublicKeyCredential<?> credential) {
        if (credential == null || credential.getRawId() == null || credential.getRawId().getBytes().length == 0
                || credential.getRawId().getBytes().length > 750 || credential.getResponse() == null
                || !PublicKeyCredentialType.PUBLIC_KEY.equals(credential.getType())
                || !credential.getRawId().toBase64UrlString().equals(credential.getId())) {
            throw failure(HttpStatus.BAD_REQUEST);
        }
    }

    private static <T> T nativeVerify(Supplier<T> verification) {
        try {
            return verification.get();
        } catch (DataAccessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw failure(HttpStatus.UNAUTHORIZED);
        }
    }

    private ResponseEntity<String> response(Object value) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").header("Content-Type", "application/json")
            .body(json.writeValueAsString(value));
    }

    private static ResponseStatusException failure(HttpStatus status) {
        return new ResponseStatusException(status, "WebAuthn request rejected");
    }

    private record Pending(UUID owner, boolean registration, Instant expiresAt, Object options) { }
}
