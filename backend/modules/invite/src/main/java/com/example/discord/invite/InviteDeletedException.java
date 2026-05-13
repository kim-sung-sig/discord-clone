package com.example.discord.invite;

public final class InviteDeletedException extends RuntimeException {
    public InviteDeletedException() {
        super("invite deleted");
    }
}
