package com.example.discord.experience;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EntitlementStore {
    Entitlement save(Entitlement entitlement);

    Optional<Entitlement> findById(UUID id);

    Optional<Entitlement> findByProviderSubscription(String provider, String providerSubscriptionId);

    List<Entitlement> findByUserAndFeature(UUID userId, String featureKey);

    List<Entitlement> findByGuild(UUID guildId);
}
