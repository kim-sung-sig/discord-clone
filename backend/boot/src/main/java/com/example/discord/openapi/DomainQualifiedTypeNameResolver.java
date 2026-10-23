package com.example.discord.openapi;

import io.swagger.v3.core.jackson.TypeNameResolver;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controllers declare request/response records with short names (several have SessionResponse or ErrorResponse).
 * Schema names default to the simple class name, so one silently overwrote another in the contract. Records nested
 * in a controller are prefixed with its domain (AuthController.SessionResponse -> AuthSessionResponse), and any
 * remaining clash fails the export instead of corrupting the generated client types.
 */
final class DomainQualifiedTypeNameResolver extends TypeNameResolver {
    // Class names, not Class objects: a restarted classloader must not look like a clash.
    private final Map<String, String> owners = new ConcurrentHashMap<>();

    @Override
    protected String getNameOfClass(Class<?> type) {
        Class<?> enclosing = type.getEnclosingClass();
        String name = super.getNameOfClass(type);
        if (enclosing != null && enclosing.getSimpleName().endsWith("Controller")) {
            String prefix = capitalize(OpenApiConfiguration.domain(enclosing.getSimpleName()));
            name = name.startsWith(prefix) ? name : prefix + name;
        }
        String owner = owners.putIfAbsent(name, type.getName());
        if (owner != null && !owner.equals(type.getName())) {
            throw new IllegalStateException("OpenAPI schema name " + name + " is used by both "
                + owner + " and " + type.getName());
        }
        return name;
    }

    private static String capitalize(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
