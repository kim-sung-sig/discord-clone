package com.example.discord.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.Profiles;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RestController;

/**
 * The committed docs/api/openapi.json is generated from the controllers. Run with -Popenapi.write=true to refresh it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "legacy-auth", "openapi-export"})
class OpenApiContractTest {
    private static final Path COMMITTED_SPEC = Path.of("..", "..", "docs", "api", "openapi.json");
    private static final Set<String> HTTP_METHODS = Set.of("get", "put", "post", "delete", "patch", "head", "options");

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private JsonNode spec;
    private String normalized;

    @BeforeEach
    void generate() throws Exception {
        String raw = mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Object tree = mapper.readValue(raw, Object.class);
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter()
            .withObjectIndenter(new DefaultIndenter("  ", "\n"))
            .withArrayIndenter(new DefaultIndenter("  ", "\n"));
        normalized = mapper.writer(printer).writeValueAsString(mapper.convertValue(tree, Map.class)) + "\n";
        spec = mapper.readTree(normalized);
    }

    @Test
    void committedSpecMatchesTheControllers() throws Exception {
        if (Boolean.getBoolean("openapi.write")) {
            Files.writeString(COMMITTED_SPEC, normalized, StandardCharsets.UTF_8);
        }
        String committed = Files.readString(COMMITTED_SPEC, StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertThat(committed)
            .as("docs/api/openapi.json is stale; run `npm run openapi:write`")
            .isEqualTo(normalized);
    }

    @Test
    void describesTheApiAndItsBearerScheme() {
        assertThat(spec.at("/info/title").asText()).isEqualTo("Discord Clone API");
        assertThat(spec.at("/info/version").asText()).isEqualTo("v1");
        assertThat(spec.at("/servers")).hasSize(1);
        assertThat(spec.at("/servers/0/url").asText()).isEqualTo("/");
        assertThat(spec.at("/components/securitySchemes/bearerAuth/type").asText()).isEqualTo("http");
        assertThat(spec.at("/components/securitySchemes/bearerAuth/scheme").asText()).isEqualTo("bearer");
    }

    @Test
    void operationIdsAreUniqueDomainQualifiedFunctionNames() {
        List<String> ids = new ArrayList<>();
        Set<String> tags = new HashSet<>();
        spec.get("paths").properties().forEach(path -> path.getValue().properties().stream()
            .filter(operation -> HTTP_METHODS.contains(operation.getKey()))
            .forEach(operation -> {
                ids.add(operation.getValue().path("operationId").asText());
                operation.getValue().path("tags").forEach(tag -> tags.add(tag.asText()));
            }));

        assertThat(ids).isNotEmpty().doesNotHaveDuplicates()
            .allSatisfy(id -> assertThat(id).matches("[a-z][A-Za-z0-9]*").doesNotMatch(".*_\\d+"));
        assertThat(tags).noneMatch(tag -> tag.endsWith("-controller"));
    }

    @Test
    void transportHeadersAreNotClientParametersAndBearerIsDeclared() {
        List<String> headerParameters = new ArrayList<>();
        int bearerOperations = 0;
        for (var path : spec.get("paths").properties()) {
            for (var operation : path.getValue().properties()) {
                operation.getValue().path("parameters").forEach(parameter -> {
                    if ("header".equals(parameter.path("in").asText())) {
                        headerParameters.add(parameter.path("name").asText());
                    }
                });
                if (operation.getValue().path("security").toString().contains("bearerAuth")) {
                    bearerOperations++;
                }
                operation.getValue().path("responses").forEach(response -> response.path("content").fieldNames()
                    .forEachRemaining(media -> assertThat(media).isEqualTo("application/json")));
            }
        }
        assertThat(headerParameters).doesNotContain("Authorization", "User-Agent");
        assertThat(bearerOperations).isGreaterThan(90);
    }

    @Test
    void sameNamedControllerRecordsKeepSeparateSchemas() {
        JsonNode schemas = spec.at("/components/schemas");
        assertThat(schemas.has("SessionResponse")).isFalse();
        assertThat(schemas.at("/AuthSessionResponse/properties").has("deviceName")).isTrue();
        assertThat(schemas.at("/GatewaySessionResponse/properties").has("lastDeliveredSequence")).isTrue();
        assertThat(spec.at("/components/schemas/AuthSessionsResponse/properties/sessions/items/$ref").asText())
            .isEqualTo("#/components/schemas/AuthSessionResponse");
    }

    @Test
    void everyRestControllerIsDocumented() {
        // Count profile-gated controllers too (postgres, legacy-auth): the contract must cover every deployment.
        var everyProfile = new StandardEnvironment() {
            @Override
            public boolean acceptsProfiles(Profiles profiles) {
                return true;
            }
        };
        var scanner = new ClassPathScanningCandidateComponentProvider(false, everyProfile);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Set<String> expected = new HashSet<>();
        for (var candidate : scanner.findCandidateComponents("com.example.discord")) {
            String location = String.valueOf(candidate.getResourceDescription()).replace('\\', '/');
            if (location.contains("/main/")) {
                String simpleName = candidate.getBeanClassName().substring(candidate.getBeanClassName().lastIndexOf('.') + 1);
                expected.add(OpenApiConfiguration.tag(simpleName));
            }
        }
        Set<String> documented = new HashSet<>();
        spec.get("paths").properties().forEach(path -> path.getValue().properties().stream()
            .filter(operation -> HTTP_METHODS.contains(operation.getKey()))
            .forEach(operation -> operation.getValue().path("tags").forEach(tag -> documented.add(tag.asText()))));

        assertThat(expected).as("main @RestController classes").hasSizeGreaterThanOrEqualTo(19);
        assertThat(documented).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void deployedConfigurationNeverServesTheApiDocs() throws Exception {
        try (var resources = Files.list(Path.of("src", "main", "resources"))) {
            for (Path file : resources.filter(path -> path.getFileName().toString().matches("application.*\\.ya?ml")).toList()) {
                var yaml = new YamlPropertiesFactoryBean();
                yaml.setResources(new FileSystemResource(file));
                Object enabled = yaml.getObject().get("springdoc.api-docs.enabled");
                if (file.getFileName().toString().equals("application.yml")) {
                    assertThat(enabled).as(file.toString()).isEqualTo(false);
                } else {
                    assertThat(enabled).as(file.toString()).isNotEqualTo(true);
                }
            }
        }
    }
}
