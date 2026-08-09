package com.example.discord.gateway;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class GatewayEventPayloadHash {
    private GatewayEventPayloadHash() {
    }

    static String of(GatewayBusEvent event) {
        String canonical = event.type() + "|" + event.guildId() + "|" + event.channelId()
            + "|" + event.payload() + "|" + event.createdAt();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
