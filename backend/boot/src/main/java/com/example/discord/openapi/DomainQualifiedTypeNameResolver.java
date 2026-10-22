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
    private final Map<String, Class<?>> owners = new ConcurrentHashMap<>();

    @Override
    protected String getNameOfClass(Class<?> type) {
        Class<?> enclosing = type.getEnclosingClass();
        String name = super.getNameOfClass(type);
        if (enclosing != null && enclosing.getSimpleName().endsWith("Controller")) {
            String prefix = capitalize(OpenApiConfiguration.domain(enclosing.getSimpleName()));
            name = type.getSimpleName().startsWith(prefix) ? type.getSimpleName() : prefix + type.getSimpleName();
        }
        Class<?> owner = owners.putIfAbsent(name, type);
        if (owner != null && !owner.equals(type)) {
            throw new IllegalStateException("OpenAPI schema name " + name + " is used by both "
                + owner.getName() + " and " + type.getName());
        }
        return name;
    }

    private static String capitalize(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
