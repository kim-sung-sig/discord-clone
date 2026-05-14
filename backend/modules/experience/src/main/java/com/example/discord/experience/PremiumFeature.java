package com.example.discord.experience;

public enum PremiumFeature {
    HD_STREAMING("hd_streaming"),
    PREMIUM_SOUNDBOARD("premium_soundboard"),
    QUEST_REWARDS("quest_rewards");

    private final String key;

    PremiumFeature(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
