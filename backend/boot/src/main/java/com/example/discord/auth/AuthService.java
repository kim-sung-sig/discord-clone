package com.example.discord.auth;

import com.example.discord.identity.AccessTokenService;
import com.example.discord.identity.EmailAddress;
import com.example.discord.identity.LoginFailureTracker;
import com.example.discord.identity.PasswordHasher;
import com.example.discord.identity.TokenVerificationException;
import com.example.discord.user.UserProfile;
import com.example.discord.user.Username;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class AuthService {
    private final AuthStore store;
    private final PasswordHasher passwordHasher;
    private final AccessTokenService accessTokenService;
    private final LoginFailureTracker loginFailures;
    private final Clock clock;

    AuthService(
        AuthStore store,
        PasswordHasher passwordHasher,
        AccessTokenService accessTokenService,
        LoginFailureTracker loginFailures,
        Clock clock
    ) {
        this.store = store;
        this.passwordHasher = passwordHasher;
        this.accessTokenService = accessTokenService;
        this.loginFailures = loginFailures;
        this.clock = clock;
    }

    AuthController.AuthResponse signup(AuthController.SignupRequest request) {
        EmailAddress email = EmailAddress.from(request.email());
        UserProfile profile = UserProfile.create(
            UUID.randomUUID(),
            Username.from(request.username()),
            request.displayName(),
            clock.instant()
        );
        AuthAccount account = new AuthAccount(
            email,
            passwordHasher.hash(request.password()),
            profile
        );
        if (!store.saveIfAbsent(account)) {
            throw new DuplicateAccountException();
        }
        return authResponse(profile);
    }

    AuthController.AuthResponse login(AuthController.LoginRequest request) {
        EmailAddress email = EmailAddress.from(request.email());
        if (loginFailures.isLocked(email)) {
            throw new LoginLockedException();
        }

        AuthAccount account = store.findByEmail(email).orElse(null);
        if (account == null) {
            recordLoginFailure(email);
            throw new InvalidCredentialsException();
        }
        if (!passwordHasher.matches(request.password(), account.passwordHash())) {
            recordLoginFailure(email);
            throw new InvalidCredentialsException();
        }

        loginFailures.clear(email);
        return authResponse(account.profile());
    }

    void logout(String token) {
        store.revokeAccessToken(token);
    }

    AuthController.UserResponse profileForToken(String token) {
        if (store.isAccessTokenRevoked(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "access token revoked");
        }
        try {
            UUID userId = accessTokenService.verify(token).userId();
            return store.findById(userId)
                .map(AuthService::userResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "user not found"));
        } catch (TokenVerificationException | IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "access token invalid", exception);
        }
    }

    private void recordLoginFailure(EmailAddress email) {
        loginFailures.recordFailure(email);
        if (loginFailures.isLocked(email)) {
            throw new LoginLockedException();
        }
    }

    private AuthController.AuthResponse authResponse(UserProfile profile) {
        return new AuthController.AuthResponse(accessTokenService.issue(profile.id()), userResponse(profile));
    }

    private static AuthController.UserResponse userResponse(UserProfile profile) {
        return new AuthController.UserResponse(
            profile.id(),
            profile.username().value(),
            profile.displayName()
        );
    }
}

class InvalidCredentialsException extends RuntimeException {
}

class LoginLockedException extends RuntimeException {
}

class DuplicateAccountException extends RuntimeException {
}
