# T00 Project Bootstrap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the initial Discord clone monorepo skeleton with Spring Boot backend, Nuxt frontend, local infrastructure, and executable test/QA harnesses.

**Architecture:** Start as a modular monorepo with a Spring Boot backend prepared for bounded-context modules and a Nuxt 3 frontend app. Keep infra local-first with Docker Compose and make all completion claims depend on executable tests, not compilation only.

**Tech Stack:** Java 21, Spring Boot 3.3.x, Gradle wrapper, JUnit 5, ArchUnit, npm workspaces, Nuxt 3, Vue 3, TypeScript, Vitest, Playwright, Docker Compose.

---

## File Structure

- Create: `settings.gradle.kts` - Gradle multi-project module registry.
- Create: `build.gradle.kts` - root Gradle conventions and dependency versions.
- Create: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.properties`, `gradle/wrapper/gradle-wrapper.jar` - Gradle wrapper generated from the official distribution.
- Create: `backend/boot/build.gradle.kts` - Spring Boot application module.
- Create: `backend/boot/src/main/java/com/example/discord/DiscordApplication.java` - backend entrypoint.
- Create: `backend/boot/src/test/java/com/example/discord/DiscordApplicationTests.java` - Spring context smoke test.
- Create: `backend/modules/permission/build.gradle.kts` - permission domain module.
- Create: `backend/modules/permission/src/main/java/com/example/discord/permission/Permission.java` - permission bitset enum.
- Create: `backend/modules/permission/src/main/java/com/example/discord/permission/PermissionSet.java` - immutable permission bitset value object.
- Create: `backend/modules/permission/src/test/java/com/example/discord/permission/PermissionSetTest.java` - TDD baseline for permission bit operations.
- Create: `backend/shared/common/build.gradle.kts` - common shared module.
- Create: `backend/shared/common/src/main/java/com/example/discord/common/Identifier.java` - typed identifier helper.
- Create: `backend/shared/common/src/test/java/com/example/discord/common/IdentifierTest.java` - identifier behavior test.
- Create: `backend/boot/src/test/java/com/example/discord/architecture/ArchitectureTest.java` - ArchUnit layer boundary baseline.
- Create: `package.json` - npm workspace root.
- Create: `apps/web/package.json` - Nuxt app dependencies and scripts.
- Create: `apps/web/nuxt.config.ts` - Nuxt config.
- Create: `apps/web/app.vue` - app shell.
- Create: `apps/web/components/shell/ServerRail.vue` - server rail component.
- Create: `apps/web/components/shell/ChannelSidebar.vue` - channel sidebar component.
- Create: `apps/web/components/shell/ChatViewport.vue` - chat viewport component.
- Create: `apps/web/components/shell/MemberSidebar.vue` - member sidebar component.
- Create: `apps/web/components/shell/UserPanel.vue` - user panel component.
- Create: `apps/web/stores/shell.ts` - Pinia shell seed state.
- Create: `apps/web/tests/components/app-shell.test.ts` - Vitest component test.
- Create: `apps/web/tests/e2e/app-shell.spec.ts` - Playwright smoke test.
- Create: `apps/web/vitest.config.ts` - component test config.
- Create: `apps/web/playwright.config.ts` - e2e config.
- Create: `apps/web/tsconfig.json` - TypeScript config.
- Create: `infra/docker/docker-compose.yml` - local PostgreSQL, Redis, Redpanda, MinIO services.
- Create: `qa/harness/README.md` - QA harness commands and gates.
- Create: `docs/03-analysis/T00-project-bootstrap.analysis.md` - Check phase template with commands.
- Create: `docs/04-report/T00-project-bootstrap.report.md` - Report template.
- Create: `docs/05-feedback/T00-project-bootstrap.feedback.md` - Feedback log.

## Task 1: Backend Permission Baseline

**Files:**
- Create: `backend/modules/permission/src/test/java/com/example/discord/permission/PermissionSetTest.java`
- Create: `backend/modules/permission/src/main/java/com/example/discord/permission/Permission.java`
- Create: `backend/modules/permission/src/main/java/com/example/discord/permission/PermissionSet.java`

- [ ] **Step 1: Write the failing test**

```java
package com.example.discord.permission;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PermissionSetTest {
    @Test
    void grantsAndRevokesIndividualPermissionsUsingBitsets() {
        PermissionSet permissions = PermissionSet.empty()
            .grant(Permission.VIEW_CHANNEL)
            .grant(Permission.SEND_MESSAGES)
            .revoke(Permission.SEND_MESSAGES);

        assertThat(permissions.allows(Permission.VIEW_CHANNEL)).isTrue();
        assertThat(permissions.allows(Permission.SEND_MESSAGES)).isFalse();
        assertThat(permissions.raw()).isEqualTo(Permission.VIEW_CHANNEL.bit());
    }

    @Test
    void administratorImpliesEveryPermission() {
        PermissionSet permissions = PermissionSet.empty().grant(Permission.ADMINISTRATOR);

        assertThat(permissions.allows(Permission.MANAGE_CHANNELS)).isTrue();
        assertThat(permissions.allows(Permission.CONNECT)).isTrue();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :backend:modules:permission:test --tests com.example.discord.permission.PermissionSetTest`

Expected: FAIL because `PermissionSet` and `Permission` do not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.example.discord.permission;

public enum Permission {
    VIEW_CHANNEL(1L << 0),
    SEND_MESSAGES(1L << 1),
    MANAGE_CHANNELS(1L << 2),
    CONNECT(1L << 3),
    ADMINISTRATOR(1L << 62);

    private final long bit;

    Permission(long bit) {
        this.bit = bit;
    }

    public long bit() {
        return bit;
    }
}
```

```java
package com.example.discord.permission;

public record PermissionSet(long raw) {
    public static PermissionSet empty() {
        return new PermissionSet(0);
    }

    public PermissionSet grant(Permission permission) {
        return new PermissionSet(raw | permission.bit());
    }

    public PermissionSet revoke(Permission permission) {
        return new PermissionSet(raw & ~permission.bit());
    }

    public boolean allows(Permission permission) {
        return (raw & Permission.ADMINISTRATOR.bit()) != 0 || (raw & permission.bit()) != 0;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :backend:modules:permission:test --tests com.example.discord.permission.PermissionSetTest`

Expected: PASS.

## Task 2: Backend Shared Identifier Baseline

**Files:**
- Create: `backend/shared/common/src/test/java/com/example/discord/common/IdentifierTest.java`
- Create: `backend/shared/common/src/main/java/com/example/discord/common/Identifier.java`

- [ ] **Step 1: Write the failing test**

```java
package com.example.discord.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentifierTest {
    @Test
    void createsIdentifierFromUuidString() {
        UUID value = UUID.randomUUID();

        Identifier identifier = Identifier.from(value.toString());

        assertThat(identifier.value()).isEqualTo(value);
        assertThat(identifier.toString()).isEqualTo(value.toString());
    }

    @Test
    void rejectsBlankIdentifier() {
        assertThatThrownBy(() -> Identifier.from(" "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("identifier must not be blank");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :backend:shared:common:test --tests com.example.discord.common.IdentifierTest`

Expected: FAIL because `Identifier` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.example.discord.common;

import java.util.UUID;

public record Identifier(UUID value) {
    public static Identifier from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("identifier must not be blank");
        }
        return new Identifier(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :backend:shared:common:test --tests com.example.discord.common.IdentifierTest`

Expected: PASS.

## Task 3: Spring Boot Smoke And Architecture Gate

**Files:**
- Create: `backend/boot/src/test/java/com/example/discord/DiscordApplicationTests.java`
- Create: `backend/boot/src/test/java/com/example/discord/architecture/ArchitectureTest.java`
- Create: `backend/boot/src/main/java/com/example/discord/DiscordApplication.java`

- [ ] **Step 1: Write failing tests**

```java
package com.example.discord;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DiscordApplicationTests {
    @Test
    void contextLoads() {
    }
}
```

```java
package com.example.discord.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.example.discord", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    @ArchTest
    static final ArchRule domainModulesDoNotDependOnBoot =
        noClasses().that().resideInAPackage("..permission..")
            .should().dependOnClassesThat().resideInAPackage("..boot..");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :backend:boot:test --tests com.example.discord.DiscordApplicationTests --tests com.example.discord.architecture.ArchitectureTest`

Expected: FAIL because application class does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.example.discord;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DiscordApplication {
    public static void main(String[] args) {
        SpringApplication.run(DiscordApplication.class, args);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :backend:boot:test`

Expected: PASS.

## Task 4: Nuxt App Shell Baseline

**Files:**
- Create: `apps/web/tests/components/app-shell.test.ts`
- Create: `apps/web/app.vue`
- Create: `apps/web/components/shell/*.vue`
- Create: `apps/web/stores/shell.ts`

- [ ] **Step 1: Write failing component test**

```ts
import { mountSuspended } from '@nuxt/test-utils/runtime'
import { describe, expect, it } from 'vitest'
import App from '../../app.vue'

describe('Discord app shell', () => {
  it('renders server rail, channel sidebar, chat viewport, member sidebar, and user panel', async () => {
    const wrapper = await mountSuspended(App)

    expect(wrapper.get('[data-testid="server-rail"]').text()).toContain('Discord Clone')
    expect(wrapper.get('[data-testid="channel-sidebar"]').text()).toContain('general')
    expect(wrapper.get('[data-testid="chat-viewport"]').text()).toContain('Welcome to the guild')
    expect(wrapper.get('[data-testid="member-sidebar"]').text()).toContain('online')
    expect(wrapper.get('[data-testid="user-panel"]').text()).toContain('vibe-coder')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm run test -w apps/web -- --run tests/components/app-shell.test.ts`

Expected: FAIL because app shell components are missing.

- [ ] **Step 3: Write minimal implementation**

Create the app shell with five components and static seed state from `stores/shell.ts`.

- [ ] **Step 4: Run test to verify it passes**

Run: `npm run test -w apps/web -- --run tests/components/app-shell.test.ts`

Expected: PASS.

## Task 5: Playwright Smoke Baseline

**Files:**
- Create: `apps/web/tests/e2e/app-shell.spec.ts`
- Create: `apps/web/playwright.config.ts`

- [ ] **Step 1: Write failing e2e test**

```ts
import { expect, test } from '@playwright/test'

test('loads Discord shell landmarks', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByTestId('server-rail')).toContainText('Discord Clone')
  await expect(page.getByTestId('channel-sidebar')).toContainText('general')
  await expect(page.getByTestId('chat-viewport')).toContainText('Welcome to the guild')
  await expect(page.getByTestId('member-sidebar')).toContainText('online')
  await expect(page.getByTestId('user-panel')).toContainText('vibe-coder')
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm run e2e -w apps/web`

Expected: FAIL before Nuxt app shell exists or before browsers are installed.

- [ ] **Step 3: Keep the app shell implementation from Task 4**

No extra production code is needed beyond Playwright config.

- [ ] **Step 4: Run test to verify it passes**

Run: `npm run e2e -w apps/web`

Expected: PASS after dependencies and browser binaries are available.

## Task 6: Local Infra And QA Docs

**Files:**
- Create: `infra/docker/docker-compose.yml`
- Create: `qa/harness/README.md`
- Create: `docs/03-analysis/T00-project-bootstrap.analysis.md`
- Create: `docs/04-report/T00-project-bootstrap.report.md`
- Create: `docs/05-feedback/T00-project-bootstrap.feedback.md`

- [ ] **Step 1: Write local infra compose**

```yaml
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: discord
      POSTGRES_USER: discord
      POSTGRES_PASSWORD: discord
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U discord -d discord"]
      interval: 5s
      timeout: 3s
      retries: 20
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
  redpanda:
    image: redpandadata/redpanda:v24.2.7
    command: ["redpanda", "start", "--overprovisioned", "--smp", "1", "--memory", "512M", "--reserve-memory", "0M", "--node-id", "0", "--check=false", "--kafka-addr", "PLAINTEXT://0.0.0.0:9092", "--advertise-kafka-addr", "PLAINTEXT://localhost:9092"]
    ports:
      - "9092:9092"
  minio:
    image: minio/minio:RELEASE.2025-04-22T22-12-26Z
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: discord
      MINIO_ROOT_PASSWORD: discord123
    ports:
      - "9000:9000"
      - "9001:9001"
```

- [ ] **Step 2: Write QA harness docs**

Document exact commands:

```powershell
.\gradlew.bat test
npm install
npm run test -w apps/web
npm run e2e -w apps/web
docker compose -f infra/docker/docker-compose.yml config
```

- [ ] **Step 3: Run compose config validation**

Run: `docker compose -f infra/docker/docker-compose.yml config`

Expected: exit 0 and rendered compose config.

## Self-Review

- Spec coverage: T00 covers backend baseline, frontend baseline, local infra, component/e2e harness, architecture test, and PDCA QA docs.
- Placeholder scan: no unresolved placeholders or vague implementation markers.
- Type consistency: Java packages use `com.example.discord`; frontend tests use `data-testid` values implemented by shell components.
