package com.example.discord.permission;

public enum Permission {
    VIEW_CHANNEL(1L << 0),
    SEND_MESSAGES(1L << 1),
    MANAGE_CHANNELS(1L << 2),
    CONNECT(1L << 3),
    ADMINISTRATOR(1L << 62);

    private final long bit;

    Permission(long bit) {
        this.bit = bit;
    }

    public long bit() {
        return bit;
    }
}
