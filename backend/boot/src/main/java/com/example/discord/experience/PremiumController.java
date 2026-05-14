package com.example.discord.experience;

import com.example.discord.auth.AuthenticatedUserResolver;
import com.example.discord.guild.InMemoryGuildService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestController
class PremiumController {
    private final InMemoryExperienceService experienceService;
    private final InMemoryGuildService guildService;
    private final BillingProvider billingProvider;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    PremiumController(
        InMemoryExperienceService experienceService,
        InMemoryGuildService guildService,
        BillingProvider billingProvider,
        AuthenticatedUserResolver authenticatedUserResolver
    ) {
        this.experienceService = experienceService;
        this.guildService = guildService;
        this.billingProvider = billingProvider;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @GetMapping("/api/premium/catalog")
    List<CatalogItem> catalog() {
        return experienceService.catalog();
    }

    @GetMapping("/api/premium/quests")
    List<Quest> quests() {
        return experienceService.quests();
    }

    @PostMapping("/api/premium/users/{userId}/entitlements")
    ResponseEntity<EntitlementResponse> grantEntitlement(
        @PathVariable UUID userId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestBody GrantEntitlementRequest request
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        if (!requesterId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "self entitlement test grant required");
        }
        requireRequest(request);
        if (!guildService.isGuildMemberOrOwner(request.guildId(), requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "guild membership required");
        }
        BillingCheckoutResult checkout = billingProvider.confirm(new BillingCheckoutCommand(
            userId,
            request.guildId(),
            request.featureKey(),
            providerSubscriptionId(userId, request),
            request.expiresAt(),
            request.simulateProviderFailure()
        ));
        if (!checkout.successful()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "billing provider failed");
        }
        Entitlement entitlement = experienceService.grantEntitlement(
            userId,
            request.guildId(),
            request.featureKey(),
            checkout.provider(),
            checkout.providerSubscriptionId(),
            checkout.expiresAt()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(EntitlementResponse.from(entitlement));
    }

    @GetMapping("/api/premium/users/{userId}/features/{featureKey}")
    PremiumGateResponse featureGate(
        @PathVariable UUID userId,
        @PathVariable String featureKey,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        if (!requesterId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "self feature check required");
        }
        return new PremiumGateResponse(userId, featureKey, experienceService.hasEntitlement(userId, featureKey));
    }

    private static void requireRequest(Object request) {
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
    }

    private static String providerSubscriptionId(UUID userId, GrantEntitlementRequest request) {
        if (request.providerSubscriptionId() != null && !request.providerSubscriptionId().isBlank()) {
            return request.providerSubscriptionId();
        }
        return "local_test:%s:%s:%s".formatted(userId, request.guildId(), request.featureKey());
    }

    record GrantEntitlementRequest(
        UUID guildId,
        String featureKey,
        String providerSubscriptionId,
        Instant expiresAt,
        boolean simulateProviderFailure
    ) {
    }

    record EntitlementResponse(
        UUID id,
        UUID userId,
        UUID guildId,
        String featureKey,
        EntitlementStatus status,
        String provider,
        String providerSubscriptionId,
        Instant expiresAt
    ) {
        static EntitlementResponse from(Entitlement entitlement) {
            return new EntitlementResponse(
                entitlement.id(),
                entitlement.userId(),
                entitlement.guildId(),
                entitlement.featureKey(),
                entitlement.status(),
                entitlement.provider(),
                entitlement.providerSubscriptionId(),
                entitlement.expiresAt()
            );
        }
    }

    record PremiumGateResponse(UUID userId, String featureKey, boolean enabled) {
    }

    record ErrorResponse(String message) {
    }
}

@RestControllerAdvice(assignableTypes = PremiumController.class)
class PremiumControllerAdvice {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<PremiumController.ErrorResponse> invalidRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new PremiumController.ErrorResponse("invalid request"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<PremiumController.ErrorResponse> unreadableRequest(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(new PremiumController.ErrorResponse("invalid request"));
    }
}
