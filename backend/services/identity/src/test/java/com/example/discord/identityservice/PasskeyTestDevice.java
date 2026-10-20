package com.example.discord.identityservice;

import com.webauthn4j.converter.util.ObjectConverter;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Test-only P-256 passkey device that emits signed wire data for the real native verifier.
 * The sign counter increases on every assertion so repeated authentications of one credential
 * are not rejected as cloned authenticators. UV is always asserted.
 */
final class PasskeyTestDevice {
    private static final byte[] RP_ID_HASH = sha256("localhost".getBytes(StandardCharsets.UTF_8));
    private final JsonMapper json = JsonMapper.builder().build();
    private final KeyPair key;
    private final byte[] id = UUID.randomUUID().toString().getBytes(StandardCharsets.US_ASCII);
    private String userHandle;
    private int signCount;

    PasskeyTestDevice() throws Exception {
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        key = generator.generateKeyPair();
    }

    int signCount() {
        return signCount;
    }

    String registration(JsonNode options, String origin) throws Exception {
        userHandle = options.at("/user/id").asText();
        var point = ((ECPublicKey) key.getPublic()).getW();
        var cbor = new ObjectConverter().getCborConverter();
        byte[] cose = cbor.writeValueAsBytes(Map.of(1, 2, 3, -7, -1, 1, -2, coordinate(point.getAffineX()), -3, coordinate(point.getAffineY())));
        byte[] data = ByteBuffer.allocate(37 + 16 + 2 + id.length + cose.length)
            .put(RP_ID_HASH).put((byte) 0x45).putInt(0)
            .put(new byte[16]).putShort((short) id.length).put(id).put(cose).array();
        byte[] attestation = cbor.writeValueAsBytes(Map.of("fmt", "none", "attStmt", Map.of(), "authData", data));
        var response = Map.of("clientDataJSON", b64(clientData(options, "webauthn.create", origin)),
            "attestationObject", b64(attestation), "transports", List.of("internal"));
        return json.writeValueAsString(Map.of("label", "PostgreSQL verification device", "credential", credential(response)));
    }

    String assertion(JsonNode options, String origin) throws Exception {
        signCount++;
        byte[] client = clientData(options, "webauthn.get", origin);
        byte[] data = ByteBuffer.allocate(37).put(RP_ID_HASH).put((byte) 5).putInt(signCount).array();
        var signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(key.getPrivate());
        signature.update(data);
        signature.update(sha256(client));
        return json.writeValueAsString(credential(Map.of("clientDataJSON", b64(client), "authenticatorData", b64(data),
            "signature", b64(signature.sign()), "userHandle", userHandle)));
    }

    private Map<String, Object> credential(Object response) {
        return Map.of("id", b64(id), "rawId", b64(id), "type", "public-key", "response", response, "clientExtensionResults", Map.of());
    }

    private byte[] clientData(JsonNode options, String type, String origin) {
        return json.writeValueAsBytes(Map.of("type", type, "challenge", options.path("challenge").asText(), "origin", origin, "crossOrigin", false));
    }

    private static byte[] coordinate(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length >= 32) {
            return Arrays.copyOfRange(bytes, bytes.length - 32, bytes.length);
        }
        byte[] padded = new byte[32];
        System.arraycopy(bytes, 0, padded, 32 - bytes.length, bytes.length);
        return padded;
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String b64(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
