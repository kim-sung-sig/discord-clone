package com.example.discord.identity;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** RFC 6238 verification; the caller must atomically consume the returned step. */
public final class TotpVerifier {
    private TotpVerifier() { }

    public static OptionalLong match(byte[] secret, String supplied, Instant now, long lastConsumedStep) {
        Objects.requireNonNull(secret, "secret");
        Objects.requireNonNull(now, "now");
        if (secret.length < 16) {
            throw new IllegalArgumentException("TOTP secret must contain at least 128 bits");
        }
        if (supplied == null || !supplied.matches("[0-9]{6}") || now.getEpochSecond() < 0) {
            return OptionalLong.empty();
        }
        long current = now.getEpochSecond() / 30;
        byte[] candidate = supplied.getBytes(StandardCharsets.US_ASCII);
        for (long step = current + 1; step >= Math.max(0, current - 1); step--) {
            if (step > lastConsumedStep && MessageDigest.isEqual(code(secret, step), candidate)) {
                return OptionalLong.of(step);
            }
        }
        return OptionalLong.empty();
    }

    private static byte[] code(byte[] secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] digest = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
            int offset = digest[digest.length - 1] & 15;
            int value = ByteBuffer.wrap(digest, offset, Integer.BYTES).getInt() & Integer.MAX_VALUE;
            return String.format(Locale.ROOT, "%06d", value % 1_000_000).getBytes(StandardCharsets.US_ASCII);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("TOTP cryptography unavailable", exception);
        }
    }
}
