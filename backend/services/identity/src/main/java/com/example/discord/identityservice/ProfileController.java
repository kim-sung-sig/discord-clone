package com.example.discord.identityservice;

import com.example.discord.identity.AccessTokenService;
import com.example.discord.identity.TokenVerificationException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/users")
class ProfileController {
    private final AccessTokenService accessTokens;

    ProfileController(AccessTokenService accessTokens) {
        this.accessTokens = accessTokens;
    }

    @GetMapping("/@me")
    ProfileResponse me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "access token invalid");
        }
        try {
            return new ProfileResponse(accessTokens.verify(authorization.substring("Bearer ".length())).userId());
        } catch (TokenVerificationException | IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "access token invalid", exception);
        }
    }

    record ProfileResponse(UUID id) {}
}
