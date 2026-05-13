package com.example.discord.invite;

public final class InviteMaxUsesExceededException extends RuntimeException {
    public InviteMaxUsesExceededException() {
        super("invite max uses exceeded");
    }
}
