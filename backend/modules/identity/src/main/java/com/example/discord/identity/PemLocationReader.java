package com.example.discord.identity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PemLocationReader {
    private PemLocationReader() {}
    public static String readPrivateKey(String location) {
        if (location == null || !location.startsWith("file:")) throw new IllegalArgumentException("key location invalid");
        return read(location);
    }

    public static String read(String location) {
        if (location == null || location.isBlank()) throw new IllegalArgumentException("key location invalid");
        try {
            if (location.startsWith("file:")) return Files.readString(Path.of(location.substring(5)));
            if (location.startsWith("classpath:")) {
                String resource = location.substring(10);
                var stream = PemLocationReader.class.getResourceAsStream(resource.startsWith("/") ? resource : "/" + resource);
                if (stream != null) return new String(stream.readAllBytes());
            }
        } catch (IOException | RuntimeException ignored) { }
        throw new IllegalArgumentException("key location invalid");
    }
}
