package com.example.discord.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TotpVerifierTest {
    private final byte[] secret = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void verifiesRfcVectorAndRejectsConsumedStep() {
        assertEquals(1L, TotpVerifier.match(secret, "287082", Instant.ofEpochSecond(59), -1).orElseThrow());
        assertTrue(TotpVerifier.match(secret, "287082", Instant.ofEpochSecond(59), 1).isEmpty());
    }

    @Test
    void acceptsOneStepDriftButRejectsOlderCodesAndMalformedInput() {
        assertEquals(1L, TotpVerifier.match(secret, "287082", Instant.ofEpochSecond(60), -1).orElseThrow());
        assertTrue(TotpVerifier.match(secret, "287082", Instant.ofEpochSecond(90), -1).isEmpty());
        assertTrue(TotpVerifier.match(secret, null, Instant.ofEpochSecond(59), -1).isEmpty());
        assertTrue(TotpVerifier.match(secret, "１２３４５６", Instant.ofEpochSecond(59), -1).isEmpty());
    }
}
