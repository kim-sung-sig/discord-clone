# Expression PostgreSQL Runtime Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make expression APIs use PostgreSQL at runtime under the `postgres` profile and remove their production in-memory service dependency.

**Architecture:** Add one domain-level `ExpressionService` port that represents the existing emoji, sticker, and reaction API. `JdbcExpressionService` implements it, while controllers use the port and tests mock it. The production configuration creates the JDBC adapter only for the `postgres` profile, so no process-local fallback exists.

**Tech Stack:** Java 21, Spring Boot, JDBC, PostgreSQL/Flyway, JUnit 5, Mockito, Gradle.

---

## File Structure

- Create: `backend/modules/expression/src/main/java/com/example/discord/expression/ExpressionService.java` - domain service port for the existing expression operations.
- Modify: `backend/boot/src/main/java/com/example/discord/expression/JdbcExpressionService.java` - implement the port and retain current JDBC queries.
- Modify: `backend/boot/src/main/java/com/example/discord/expression/ExpressionConfiguration.java` - register only the JDBC adapter under `postgres`.
- Modify: `backend/boot/src/main/java/com/example/discord/expression/ExpressionController.java` - inject the port, not the in-memory concrete type.
- Modify: `backend/boot/src/test/java/com/example/discord/expression/ExpressionControllerTest.java` - construct the controller with a mocked port.
- Create: `backend/boot/src/test/java/com/example/discord/expression/ExpressionRuntimeConfigurationTest.java` - assert the `postgres` runtime selects JDBC and a fresh adapter reads persisted data.
- Delete: `backend/modules/expression/src/main/java/com/example/discord/expression/InMemoryExpressionService.java` - remove the production in-memory implementation after callers are migrated.

### Task 1: Define the expression port

**Files:**
- Create: `backend/modules/expression/src/main/java/com/example/discord/expression/ExpressionService.java`

- [ ] **Step 1: Write the failing compilation test through the port**

In `ExpressionControllerTest`, replace the concrete field/constructor argument with `ExpressionService expressionService` so compilation fails before the port exists.

- [ ] **Step 2: Run the focused controller test**

Run: `./gradlew :backend:boot:test --tests com.example.discord.expression.ExpressionControllerTest`

Expected: FAIL during compilation because `ExpressionService` does not exist.

- [ ] **Step 3: Add the minimal port**

```java
public interface ExpressionService {
    CustomEmoji createCustomEmoji(UUID guildId, String name, String imageObjectKey, UUID creatorId);
    List<CustomEmoji> customEmojis(UUID guildId);
    void deleteCustomEmoji(UUID guildId, UUID emojiId);
    Sticker createSticker(UUID guildId, String name, String description, UUID creatorId);
    List<Sticker> stickers(UUID guildId);
    void addReaction(UUID channelId, UUID messageId, String emojiKey, UUID userId);
    void removeReaction(UUID channelId, UUID messageId, String emojiKey, UUID userId);
    List<ReactionSummary> reactionSummaries(UUID channelId, UUID messageId);
}
```

- [ ] **Step 4: Run the focused controller test**

Run: `./gradlew :backend:boot:test --tests com.example.discord.expression.ExpressionControllerTest`

Expected: still FAIL until the controller and tests use the new port consistently.

### Task 2: Switch the controller test and controller dependency

**Files:**
- Modify: `backend/boot/src/main/java/com/example/discord/expression/ExpressionController.java`
- Modify: `backend/boot/src/test/java/com/example/discord/expression/ExpressionControllerTest.java`

- [ ] **Step 1: Make the controller test use a mock port**

```java
ExpressionService expressionService = mock(ExpressionService.class);
ExpressionController controller = new ExpressionController(
    expressionService, guildService, messageLookup, authenticatedUserResolver
);
```

- [ ] **Step 2: Run the focused test for RED**

Run: `./gradlew :backend:boot:test --tests com.example.discord.expression.ExpressionControllerTest`

Expected: FAIL because `ExpressionController` still requires `InMemoryExpressionService`.

- [ ] **Step 3: Change the controller field and constructor type**

```java
private final ExpressionService expressionService;

ExpressionController(
    ExpressionService expressionService,
    InMemoryGuildService guildService,
    MessageLookupPort messageLookup,
    AuthenticatedUserResolver authenticatedUserResolver
) {
    this.expressionService = expressionService;
    this.guildService = guildService;
    this.messageLookup = messageLookup;
    this.authenticatedUserResolver = authenticatedUserResolver;
}
```

- [ ] **Step 4: Run the focused test for GREEN**

Run: `./gradlew :backend:boot:test --tests com.example.discord.expression.ExpressionControllerTest`

Expected: PASS without PostgreSQL or Redis.

### Task 3: Register the PostgreSQL runtime implementation

**Files:**
- Modify: `backend/boot/src/main/java/com/example/discord/expression/JdbcExpressionService.java`
- Modify: `backend/boot/src/main/java/com/example/discord/expression/ExpressionConfiguration.java`
- Create: `backend/boot/src/test/java/com/example/discord/expression/ExpressionRuntimeConfigurationTest.java`

- [ ] **Step 1: Write the failing profile-selection test**

```java
@SpringBootTest
@ActiveProfiles("postgres")
@EnabledIfEnvironmentVariable(named = "DISCORD_RUN_POSTGRES_TESTS", matches = "true")
class ExpressionRuntimeConfigurationTest {
    @Autowired ExpressionService expressionService;

    @Test
    void postgresProfileUsesJdbcExpressionService() {
        assertThat(expressionService).isInstanceOf(JdbcExpressionService.class);
    }
}
```

- [ ] **Step 2: Run the profile-selection test for RED**

Run: `DISCORD_RUN_POSTGRES_TESTS=true ./gradlew :backend:boot:test --tests com.example.discord.expression.ExpressionRuntimeConfigurationTest`

Expected: FAIL because the active bean is `InMemoryExpressionService`.

- [ ] **Step 3: Implement only profile wiring**

```java
// JdbcExpressionService
final class JdbcExpressionService implements ExpressionService { ... }

// ExpressionConfiguration
@Bean
@Profile("postgres")
ExpressionService expressionService(DataSource dataSource) {
    return new JdbcExpressionService(dataSource);
}
```

Do not register an in-memory default bean.

- [ ] **Step 4: Run the profile-selection test for GREEN**

Run: `DISCORD_RUN_POSTGRES_TESTS=true ./gradlew :backend:boot:test --tests com.example.discord.expression.ExpressionRuntimeConfigurationTest`

Expected: PASS with a `JdbcExpressionService` bean.

### Task 4: Verify persistence and remove the production in-memory implementation

**Files:**
- Modify: `backend/boot/src/test/java/com/example/discord/expression/ExpressionRuntimeConfigurationTest.java`
- Delete: `backend/modules/expression/src/main/java/com/example/discord/expression/InMemoryExpressionService.java`

- [ ] **Step 1: Add the failing fresh-adapter persistence test**

```java
@Test
void jdbcExpressionServiceReadsEmojiWrittenByAnotherAdapter() {
    JdbcExpressionService writer = new JdbcExpressionService(dataSource);
    CustomEmoji emoji = writer.createCustomEmoji(GUILD_ID, "shipit_2026", "emoji/shipit.png", USER_ID);

    assertThat(new JdbcExpressionService(dataSource).customEmojis(GUILD_ID))
        .extracting(CustomEmoji::id)
        .containsExactly(emoji.id());
}
```

- [ ] **Step 2: Run the PostgreSQL runtime tests**

Run: `DISCORD_RUN_POSTGRES_TESTS=true ./gradlew :backend:boot:test --tests "com.example.discord.expression.*"`

Expected: PASS after the adapter is selected; the test proves the second adapter reads database state.

- [ ] **Step 3: Delete the in-memory production class**

Delete `InMemoryExpressionService.java` only after `rg -n "InMemoryExpressionService" backend --glob "*.java"` shows no runtime caller.

- [ ] **Step 4: Run final focused verification**

Run:

```bash
./gradlew :backend:boot:test --tests com.example.discord.expression.ExpressionControllerTest
DISCORD_RUN_POSTGRES_TESTS=true ./gradlew :backend:boot:test --tests "com.example.discord.expression.*"
rg -n "InMemoryExpressionService" backend/boot/src/main --glob "*.java"
git diff --check
```

Expected: both test commands pass, the final search has no output, and `git diff --check` has no output.

- [ ] **Step 5: Commit**

```bash
git add backend/modules/expression backend/boot/src/main/java/com/example/discord/expression backend/boot/src/test/java/com/example/discord/expression
git commit -m "Use PostgreSQL for expression runtime"
```

Do not stage unrelated `.gitignore` changes.
