package com.example.discord.invite;

public final class InviteNotFoundException extends RuntimeException {
    public InviteNotFoundException() {
        super("invite not found");
    }
}
