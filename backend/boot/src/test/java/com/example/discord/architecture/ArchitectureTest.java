package com.example.discord.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

@AnalyzeClasses(packages = "com.example.discord", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    @ArchTest
    static final ArchRule domainModulesDoNotDependOnBoot =
        noClasses().that().resideInAnyPackage(
                "..channel..",
                "..gateway..",
                "..guild..",
                "..identity..",
                "..invite..",
                "..message..",
                "..permission..",
                "..user.."
            )
            .should().dependOnClassesThat().resideInAPackage("..boot..");

    @Test
    void backendModulesDoNotImportBootAdapters() throws Exception {
        Path modulesRoot = Path.of("..", "modules").normalize();
        List<String> forbiddenImports;
        try (var files = Files.walk(modulesRoot)) {
            forbiddenImports = files
                .filter(path -> path.toString().endsWith(".java"))
                .flatMap(path -> readLines(path).stream()
                    .filter(line -> line.startsWith("import "))
                    .filter(line -> line.contains("Controller")
                        || line.contains("Configuration")
                        || line.contains("Advice"))
                    .map(line -> path + ": " + line))
                .toList();
        }

        assertThat(forbiddenImports).isEmpty();
    }

    @Test
    void backendModulesDoNotImportSpringOrJakartaFrameworks() throws Exception {
        Path modulesRoot = Path.of("..", "modules").normalize();
        List<String> forbiddenImports;
        try (var files = Files.walk(modulesRoot)) {
            forbiddenImports = files
                .filter(path -> path.toString().endsWith(".java"))
                .flatMap(path -> readLines(path).stream()
                    .filter(line -> line.startsWith("import "))
                    .filter(line -> line.contains("org.springframework.")
                        || line.contains("jakarta."))
                    .map(line -> path + ": " + line))
                .toList();
        }

        assertThat(forbiddenImports).isEmpty();
    }

    private static List<String> readLines(Path path) {
        try {
            return Files.readAllLines(path);
        } catch (Exception exception) {
            throw new IllegalStateException("failed to read " + path, exception);
        }
    }
}
