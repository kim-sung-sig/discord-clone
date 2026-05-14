package com.example.discord.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryModerationServiceTest {
    @Test
    void onboardingAnswersGrantOnlyConfiguredRoles() {
        InMemoryModerationService service = new InMemoryModerationService();
        UUID guildId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID configuredRoleId = UUID.randomUUID();
        UUID unconfiguredRoleId = UUID.randomUUID();

        OnboardingQuestion question = service.createOnboardingQuestion(
            guildId,
            "Choose your access",
            List.of(new OnboardingAnswer(UUID.randomUUID(), "Announcements", List.of(configuredRoleId))),
            UUID.randomUUID()
        );

        List<UUID> grantedRoleIds = service.submitOnboardingAnswers(
            guildId,
            memberId,
            List.of(question.answers().getFirst().id(), unconfiguredRoleId)
        );

        assertThat(grantedRoleIds).containsExactly(configuredRoleId);
    }

    @Test
    void keywordAutoModRuleBlocksMatchingMessage() {
        InMemoryModerationService service = new InMemoryModerationService();
        UUID guildId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();

        AutoModRule rule = service.createAutoModRule(
            guildId,
            UUID.randomUUID(),
            AutoModRuleType.KEYWORD,
            "blocked words",
            List.of("spoiler")
        );

        AutoModDecision decision = service.evaluateMessage(guildId, channelId, authorId, "contains Spoiler text");

        assertThat(decision.blocked()).isTrue();
        assertThat(decision.ruleId()).contains(rule.id());
    }

    @Test
    void autoModRuleCreationAndBlockAppendAuditEntries() {
        InMemoryModerationService service = new InMemoryModerationService();
        UUID guildId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        service.createAutoModRule(guildId, actorId, AutoModRuleType.KEYWORD, "blocked words", List.of("spoiler"));
        service.evaluateMessage(guildId, UUID.randomUUID(), UUID.randomUUID(), "spoiler");

        assertThat(service.auditLogs(guildId))
            .extracting(AuditLogEntry::action)
            .containsExactly(AuditLogAction.AUTOMOD_MESSAGE_BLOCKED, AuditLogAction.AUTOMOD_RULE_CREATED);
    }
}
