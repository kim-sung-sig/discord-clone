package com.example.discord.invite;

import java.util.UUID;

public interface InviteService {
    Invite create(CreateInviteCommand command);
    Invite preview(String code);
    Invite get(String code);
    InviteAcceptResult accept(String code, UUID memberId);
    Invite delete(String code);
}
