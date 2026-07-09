package com.example.discord.experience;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
class ExperienceConfiguration {
    @Bean
    InMemoryExperienceService experienceService(EntitlementStore entitlementStore) {
        return new InMemoryExperienceService(java.time.Clock.systemUTC(), entitlementStore);
    }

    @Bean
    @Profile("!postgres")
    EntitlementStore entitlementStore() {
        return new InMemoryEntitlementStore();
    }

    @Bean
    BillingProvider billingProvider() {
        return new LocalTestBillingProvider();
    }
}
