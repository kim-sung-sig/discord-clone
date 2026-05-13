package com.example.discord.auth;

import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
class AuthController {
    private final AuthService authService;

    AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/signup")
    ResponseEntity<AuthResponse> signup(@RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request));
    }

    @PostMapping("/auth/login")
    AuthResponse login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/auth/logout")
    ResponseEntity<Void> logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            authService.logout(bearerToken(authorization));
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/@me")
    UserResponse me(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        String token = bearerToken(authorization);
        return authService.profileForToken(token);
    }

    private static String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "bearer token required");
        }
        return authorization.substring("Bearer ".length());
    }

    record SignupRequest(String email, String username, String displayName, String password) {
    }

    record LoginRequest(String email, String password) {
    }

    record AuthResponse(String accessToken, UserResponse user) {
    }

    record UserResponse(UUID id, String username, String displayName) {
    }

    record ErrorResponse(String message) {
    }
}

@RestControllerAdvice(assignableTypes = AuthController.class)
class AuthControllerAdvice {
    @ExceptionHandler(LoginLockedException.class)
    ResponseEntity<AuthController.ErrorResponse> loginLocked(LoginLockedException exception) {
        return ResponseEntity.status(HttpStatus.LOCKED)
            .body(new AuthController.ErrorResponse("login temporarily locked"));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<AuthController.ErrorResponse> invalidCredentials(InvalidCredentialsException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new AuthController.ErrorResponse("invalid credentials"));
    }

    @ExceptionHandler(DuplicateAccountException.class)
    ResponseEntity<AuthController.ErrorResponse> duplicateAccount(DuplicateAccountException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new AuthController.ErrorResponse("account already exists"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<AuthController.ErrorResponse> invalidRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
            .body(new AuthController.ErrorResponse("invalid request"));
    }
}
