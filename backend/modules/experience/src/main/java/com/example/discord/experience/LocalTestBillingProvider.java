package com.example.discord.experience;

public final class LocalTestBillingProvider implements BillingProvider {
    public static final String PROVIDER = "local_test";

    @Override
    public BillingCheckoutResult confirm(BillingCheckoutCommand command) {
        if (command.simulateProviderFailure()) {
            return BillingCheckoutResult.failure("simulated provider failure");
        }
        return BillingCheckoutResult.success(PROVIDER, command.providerSubscriptionId(), command.expiresAt());
    }
}
