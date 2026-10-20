package com.example.discord.identityservice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SecretCipherTest {
    @Test
    void randomizesCiphertextAndRejectsOwnerSubstitutionAndTampering() {
        SecretCipher cipher = new SecretCipher(new byte[32]);
        UUID owner = UUID.randomUUID();
        byte[] secret = new byte[20];
        String encrypted = cipher.encrypt(owner, secret);
        assertNotEquals(encrypted, cipher.encrypt(owner, secret));
        assertArrayEquals(secret, cipher.decrypt(owner, encrypted));
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(UUID.randomUUID(), encrypted));
        byte[] corrupted = Base64.getDecoder().decode(encrypted);
        corrupted[corrupted.length - 1] ^= 1;
        assertThrows(IllegalStateException.class,
            () -> cipher.decrypt(owner, Base64.getEncoder().encodeToString(corrupted)));
    }
}
