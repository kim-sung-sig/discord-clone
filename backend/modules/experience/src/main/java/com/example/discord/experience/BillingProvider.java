package com.example.discord.experience;

public interface BillingProvider {
    BillingCheckoutResult confirm(BillingCheckoutCommand command);
}
