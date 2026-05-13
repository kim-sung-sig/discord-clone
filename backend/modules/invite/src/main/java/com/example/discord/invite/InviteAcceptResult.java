package com.example.discord.invite;

import java.util.UUID;

public record InviteAcceptResult(Invite invite, UUID memberId, boolean alreadyAccepted) {
    public InviteAcceptResult {
        if (invite == null) {
            throw new IllegalArgumentException("invite is required");
        }
        if (memberId == null) {
            throw new IllegalArgumentException("memberId is required");
        }
    }
}
