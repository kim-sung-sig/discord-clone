package com.example.discord.openapi;

import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.method.HandlerMethod;

/**
 * Code-first OpenAPI contract consumed by the generated web client (orval).
 * Controllers stay annotation-free: names come from the controller class and handler method.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {
    static final String BEARER_SCHEME = "bearerAuth";
    private static final String CONTROLLER_SUFFIX = "Controller";
    private static final String ANY_MEDIA_TYPE = "*/*";

    @Bean
    OpenAPI discordOpenApi() {
        return new OpenAPI()
            .info(new Info().title("Discord Clone API").version("v1"))
            .servers(List.of(new Server().url("/")))
            .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }

    // Replaces springdoc's default ModelResolver (same converter class) so schema names cannot collide.
    @Bean
    ModelResolver domainQualifiedModelResolver(ObjectMapperProvider objectMapperProvider) {
        return new ModelResolver(objectMapperProvider.jsonMapper(), new DomainQualifiedTypeNameResolver())
            .openapi31(objectMapperProvider.isOpenapi31());
    }

    // operationId becomes the generated client function name, so make it unique and domain-qualified:
    // MessageController#list -> messageList, AuthController#login -> authLogin.
    @Bean
    OperationCustomizer domainQualifiedOperations() {
        return (Operation operation, HandlerMethod handler) -> {
            String domain = domain(handler.getBeanType().getSimpleName());
            String method = handler.getMethod().getName();
            operation.setOperationId(domain + Character.toUpperCase(method.charAt(0)) + method.substring(1));
            operation.setTags(List.of(tag(handler.getBeanType().getSimpleName())));
            // Authorization is the bearerAuth scheme (set by the client), and User-Agent belongs to the browser.
            List<Parameter> parameters = operation.getParameters() == null ? List.of() : operation.getParameters();
            boolean bearer = parameters.stream().anyMatch(parameter -> isHeader(parameter, HttpHeaders.AUTHORIZATION));
            List<Parameter> kept = parameters.stream()
                .filter(parameter -> !isHeader(parameter, HttpHeaders.AUTHORIZATION)
                    && !isHeader(parameter, HttpHeaders.USER_AGENT))
                .toList();
            operation.setParameters(kept.isEmpty() ? null : new ArrayList<>(kept));
            if (bearer) {
                operation.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
            }
            return operation;
        };
    }

    // REST controllers serialize JSON; springdoc reports "*/*" when a handler does not declare produces.
    @Bean
    OpenApiCustomizer jsonResponses() {
        return openApi -> openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation ->
            operation.getResponses().values().forEach(response -> {
                Content content = response.getContent();
                if (content != null && content.containsKey(ANY_MEDIA_TYPE)) {
                    content.addMediaType(MediaType.APPLICATION_JSON_VALUE, content.remove(ANY_MEDIA_TYPE));
                }
            })));
    }

    private static boolean isHeader(Parameter parameter, String name) {
        return "header".equals(parameter.getIn()) && name.equalsIgnoreCase(parameter.getName());
    }

    static String domain(String controllerName) {
        String base = controllerName.endsWith(CONTROLLER_SUFFIX)
            ? controllerName.substring(0, controllerName.length() - CONTROLLER_SUFFIX.length())
            : controllerName;
        return Character.toLowerCase(base.charAt(0)) + base.substring(1);
    }

    static String tag(String controllerName) {
        return domain(controllerName).replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
    }
}
