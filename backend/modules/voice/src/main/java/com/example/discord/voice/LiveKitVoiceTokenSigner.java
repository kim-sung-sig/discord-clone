package com.example.discord.voice;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class LiveKitVoiceTokenSigner implements VoiceTokenSigner {
    private static final String HMAC_SHA256 = "HmacSHA256";

    private final String apiKey;
    private final byte[] apiSecret;

    public LiveKitVoiceTokenSigner(String apiKey, String apiSecret) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey is required");
        }
        if (apiSecret == null || apiSecret.length() < 32) {
            throw new IllegalArgumentException("apiSecret must be at least 32 characters");
        }
        this.apiKey = apiKey;
        this.apiSecret = apiSecret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public VoiceRoomToken sign(VoiceTokenSigningRequest request) {
        String room = "guild:%s:voice:%s".formatted(request.guildId(), request.channelId());
        String participant = "user:%s".formatted(request.userId());
        Instant expiresAt = request.issuedAt().plusSeconds(request.ttlSeconds());
        String token = jwt(request, room, participant, expiresAt);
        return new VoiceRoomToken(room, participant, token, VoiceRoomToken.LIVEKIT_PROVIDER, expiresAt);
    }

    private String jwt(VoiceTokenSigningRequest request, String room, String participant, Instant expiresAt) {
        String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String payload = """
            {"iss":"%s","sub":"%s","nbf":%d,"exp":%d,"video":{"roomJoin":true,"room":"%s"},"guildId":"%s","channelId":"%s","userId":"%s","metadata":"{\\"guildId\\":\\"%s\\",\\"channelId\\":\\"%s\\",\\"userId\\":\\"%s\\"}"}
            """.formatted(
            json(apiKey),
            json(participant),
            request.issuedAt().getEpochSecond(),
            expiresAt.getEpochSecond(),
            json(room),
            request.guildId(),
            request.channelId(),
            request.userId(),
            request.guildId(),
            request.channelId(),
            request.userId()
        ).strip();
        String unsigned = base64Url(header.getBytes(StandardCharsets.UTF_8))
            + "."
            + base64Url(payload.getBytes(StandardCharsets.UTF_8));
        return unsigned + "." + signature(unsigned);
    }

    private String signature(String unsigned) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(apiSecret, HMAC_SHA256));
            return base64Url(mac.doFinal(unsigned.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("unable to sign LiveKit token", exception);
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String json(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"");
    }
}
