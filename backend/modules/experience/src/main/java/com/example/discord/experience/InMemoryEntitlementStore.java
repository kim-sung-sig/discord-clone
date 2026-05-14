package com.example.discord.experience;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class InMemoryEntitlementStore implements EntitlementStore {
    private final Map<UUID, Entitlement> entitlementsById = new LinkedHashMap<>();

    @Override
    public synchronized Entitlement save(Entitlement entitlement) {
        entitlementsById.put(entitlement.id(), entitlement);
        return entitlement;
    }

    @Override
    public synchronized Optional<Entitlement> findById(UUID id) {
        return Optional.ofNullable(entitlementsById.get(id));
    }

    @Override
    public synchronized Optional<Entitlement> findByProviderSubscription(String provider, String providerSubscriptionId) {
        return entitlementsById.values().stream()
            .filter(entitlement -> entitlement.provider().equals(provider))
            .filter(entitlement -> entitlement.providerSubscriptionId().equals(providerSubscriptionId))
            .findFirst();
    }

    @Override
    public synchronized List<Entitlement> findByUserAndFeature(UUID userId, String featureKey) {
        return entitlementsById.values().stream()
            .filter(entitlement -> entitlement.userId().equals(userId))
            .filter(entitlement -> entitlement.featureKey().equals(featureKey))
            .toList();
    }

    @Override
    public synchronized List<Entitlement> findByGuild(UUID guildId) {
        return entitlementsById.values().stream()
            .filter(entitlement -> entitlement.guildId().equals(guildId))
            .toList();
    }
}
