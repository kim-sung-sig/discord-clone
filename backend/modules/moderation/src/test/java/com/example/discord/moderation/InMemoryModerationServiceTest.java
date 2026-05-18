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

    @Test
    void reportingMessageAddsPendingReportAndAuditEntry() {
        InMemoryModerationService service = new InMemoryModerationService();
        UUID guildId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();

        MessageReport report = service.reportMessage(new ReportMessageCommand(
            guildId,
            channelId,
            messageId,
            reporterId,
            "harassment"
        ));

        assertThat(report.status()).isEqualTo(MessageReportStatus.OPEN);
        assertThat(service.pendingMessageReports(guildId)).extracting(MessageReport::id).containsExactly(report.id());
        assertThat(service.auditLogs(guildId))
            .extracting(AuditLogEntry::action)
            .containsExactly(AuditLogAction.MESSAGE_REPORTED);
    }

    @Test
    void resolvingMessageReportRemovesItFromPendingQueueAndAddsModeratorAudit() {
        InMemoryModerationService service = new InMemoryModerationService();
        UUID guildId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID moderatorId = UUID.randomUUID();
        MessageReport report = service.reportMessage(new ReportMessageCommand(
            guildId,
            channelId,
            messageId,
            UUID.randomUUID(),
            "spam"
        ));

        MessageReport resolved = service.resolveMessageReport(
            guildId,
            report.id(),
            moderatorId,
            MessageReportStatus.RESOLVED,
            "removed message"
        );

        assertThat(resolved.status()).isEqualTo(MessageReportStatus.RESOLVED);
        assertThat(resolved.moderatorId()).contains(moderatorId);
        assertThat(service.pendingMessageReports(guildId)).isEmpty();
        assertThat(service.messageReports(guildId, messageId)).extracting(MessageReport::id).containsExactly(report.id());
        assertThat(service.auditLogs(guildId))
            .extracting(AuditLogEntry::action)
            .containsExactly(AuditLogAction.MESSAGE_REPORT_RESOLVED, AuditLogAction.MESSAGE_REPORTED);
    }
}
