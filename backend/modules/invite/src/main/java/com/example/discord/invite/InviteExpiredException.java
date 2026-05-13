package com.example.discord.invite;

public final class InviteExpiredException extends RuntimeException {
    public InviteExpiredException() {
        super("invite expired");
    }
}
