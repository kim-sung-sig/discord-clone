package com.example.discord.identityservice;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class SecretCipher {
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    SecretCipher(byte[] key) {
        if (key == null || key.length != 32) {
            throw new IllegalArgumentException("MFA encryption key must be 32 bytes");
        }
        this.key = new SecretKeySpec(key, "AES");
    }

    String encrypt(UUID owner, byte[] plaintext) {
        byte[] nonce = new byte[12];
        random.nextBytes(nonce);
        byte[] encrypted = transform(Cipher.ENCRYPT_MODE, owner, nonce, plaintext);
        byte[] result = Arrays.copyOf(nonce, nonce.length + encrypted.length);
        System.arraycopy(encrypted, 0, result, nonce.length, encrypted.length);
        return Base64.getEncoder().encodeToString(result);
    }

    byte[] decrypt(UUID owner, String encoded) {
        byte[] bytes = Base64.getDecoder().decode(encoded);
        if (bytes.length < 28) {
            throw new IllegalStateException("MFA ciphertext invalid");
        }
        return transform(Cipher.DECRYPT_MODE, owner, Arrays.copyOf(bytes, 12), Arrays.copyOfRange(bytes, 12, bytes.length));
    }

    private byte[] transform(int mode, UUID owner, byte[] nonce, byte[] value) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, key, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(owner.toString().getBytes(StandardCharsets.US_ASCII));
            return cipher.doFinal(value);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("MFA secret encryption failed");
        }
    }
}
