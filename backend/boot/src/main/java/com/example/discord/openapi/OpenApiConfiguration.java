package com.example.discord.openapi;

import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.method.HandlerMethod;

/**
 * Code-first OpenAPI contract consumed by the generated web client (orval).
 * Names come from controller classes and handler methods; ResponseStatus declares dynamic success statuses.
 */
@Configuration(proxyBeanMethods = false)
// Same conditions as springdoc's SpringDocConfiguration, which provides ObjectMapperProvider: deployed profiles
// disable api-docs and admin-cli runs without a web server, and neither may fail to start because of this class.
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", matchIfMissing = true)
class OpenApiConfiguration {
    static final String BEARER_SCHEME = "bearerAuth";
    private static final String CONTROLLER_SUFFIX = "Controller";
    private static final String ANY_MEDIA_TYPE = "*/*";
    // Response fields are present by default. These are the actual null-producing domain projections.
    private static final Map<String, Set<String>> NULLABLE_RESPONSES = Map.ofEntries(
        Map.entry("GuildChannelResponse", Set.of("parentId")),
        Map.entry("UserGuildChannelResponse", Set.of("parentId")),
        Map.entry("InviteResponse", Set.of("expiresAt")),
        Map.entry("MessagePageResponse", Set.of("nextCursor")),
        Map.entry("MessageResponse", Set.of("authorDisplayName")),
        Map.entry("GatewayEventResponse", Set.of("guildId", "channelId")),
        Map.entry("PremiumEntitlementResponse", Set.of("expiresAt")),
        Map.entry("AttachmentResponse", Set.of("messageId")),
        Map.entry("ModerationAuditLogEntryResponse", Set.of("targetId", "reason")),
        Map.entry("ModerationMessageReportResponse", Set.of("moderatorId")),
        Map.entry("NotificationResponse", Set.of("guildId", "eventId", "targetUserId")),
        Map.entry("MessageOutboxMessageAuthorResponse", Set.of("userId", "botId", "webhookId")),
        Map.entry("MessageOutboxMessageTargetResponse", Set.of("guildId", "channelId", "threadId", "conversationId", "recipientId"))
    );

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
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation ->
                operation.getResponses().values().forEach(response -> {
                    Content content = response.getContent();
                    if (content != null && content.containsKey(ANY_MEDIA_TYPE)) {
                        content.addMediaType(MediaType.APPLICATION_JSON_VALUE, content.remove(ANY_MEDIA_TYPE));
                    }
                })));
        };
    }

    @Bean
    @SuppressWarnings("unchecked") // Swagger Components exposes raw Schema values and their property maps.
    OpenApiCustomizer responseContracts() {
        return openApi -> {
            var schemas = openApi.getComponents().getSchemas();
            if (schemas != null) {
                schemas.forEach((name, rawSchema) -> {
                    Schema<?> schema = rawSchema;
                    // Request omission/default semantics are independent of response presence.
                    if (!name.endsWith("Request") && schema.getProperties() != null) {
                        schema.setRequired(new ArrayList<>(schema.getProperties().keySet()));
                        for (String property : NULLABLE_RESPONSES.getOrDefault(name, Set.of())) {
                            Schema<?> value = schema.getProperties().get(property);
                            if (value != null) {
                                Set<String> types = value.getTypes() == null
                                    ? new LinkedHashSet<>() : new LinkedHashSet<>(value.getTypes());
                                if (value.getType() != null) {
                                    types.add(value.getType());
                                }
                                types.add("null");
                                value.setTypes(types);
                            }
                        }
                    }
                });
            }
            Schema<?> problem = new Schema<>().type("object")
                .addProperty("type", new StringSchema().format("uri"))
                .addProperty("title", new StringSchema())
                .addProperty("status", new IntegerSchema().format("int32"))
                .addProperty("detail", new StringSchema())
                .addProperty("instance", new StringSchema().format("uri"))
                .addProperty("code", new StringSchema())
                .addProperty("requestId", new StringSchema())
                .required(List.of("type", "title", "status", "detail", "instance", "code", "requestId"));
            openApi.getComponents().addSchemas("ApiProblem", problem);
            if (openApi.getPaths() != null) {
                openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
                    // Global advice documents the common recovery contract, including unexpected failures.
                    for (int status : List.of(400, 401, 403, 404, 405, 406, 409, 410, 413, 415, 423, 429, 500, 503)) {
                        operation.getResponses().addApiResponse(String.valueOf(status), new ApiResponse()
                            .description(org.springframework.http.HttpStatus.valueOf(status).getReasonPhrase())
                            .content(new Content().addMediaType(MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                new io.swagger.v3.oas.models.media.MediaType()
                                    .schema(new Schema<>().$ref("#/components/schemas/ApiProblem")))));
                    }
                    operation.getResponses().addApiResponse("default", new ApiResponse()
                        .description("Other errors")
                        .content(new Content().addMediaType(MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            new io.swagger.v3.oas.models.media.MediaType()
                                .schema(new Schema<>().$ref("#/components/schemas/ApiProblem")))));
                    ApiResponse noContent = operation.getResponses().get("204");
                    if (noContent != null) {
                        noContent.setContent(null);
                    }
                }));
            }
        };
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
