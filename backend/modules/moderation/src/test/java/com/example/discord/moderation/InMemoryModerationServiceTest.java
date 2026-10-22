package com.example.discord.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryModerationServiceTest {
    @Test
    void sharedRulePolicySkipsDisabledAndSpamAndNormalizesKeywords() {
        UUID guild = UUID.randomUUID();
        var keyword = new AutoModRule(UUID.randomUUID(), guild, AutoModRuleType.KEYWORD, "rule", List.of(" Spoiler "), true);
        assertThat(keyword.matches("a SPOILER here")).isTrue();
        assertThat(keyword.matches(null)).isFalse();
        assertThat(new AutoModRule(keyword.id(), guild, keyword.type(), keyword.name(), keyword.keywords(), false).matches("spoiler")).isFalse();
        assertThat(new AutoModRule(keyword.id(), guild, AutoModRuleType.SPAM, keyword.name(), keyword.keywords(), true).matches("spoiler")).isFalse();
    }

    @Test
    void sharedReportResolutionRejectsOpenAndPreservesIdentity() {
        var service = new InMemoryModerationService();
        var report = service.reportMessage(new ReportMessageCommand(UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), UUID.randomUUID(), "reason"));
        UUID moderator = UUID.randomUUID();
        assertThatThrownBy(() -> report.resolve(moderator, MessageReportStatus.OPEN, "", java.time.Instant.now()))
            .isInstanceOf(IllegalArgumentException.class);
        var resolved = report.resolve(moderator, MessageReportStatus.RESOLVED, null, java.time.Instant.now());
        assertThat(resolved.id()).isEqualTo(report.id());
        assertThat(resolved.createdAt()).isEqualTo(report.createdAt());
        assertThat(resolved.moderatorId()).contains(moderator);
        assertThat(resolved.resolution()).isEmpty();
    }

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

    @Test
    void auditLogReadsAreBoundedNewestFirst() {
        InMemoryModerationService service = new InMemoryModerationService();
        UUID guildId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        for (int index = 0; index < 60; index++) {
            service.appendAudit(
                guildId,
                AuditLogAction.MESSAGE_REPORTED,
                actorId,
                UUID.randomUUID(),
                "report-" + index
            );
        }

        assertThat(service.auditLogs(guildId))
            .hasSize(50)
            .first()
            .extracting(AuditLogEntry::reason)
            .isEqualTo("report-59");
        assertThat(service.auditLogs(guildId, null, null, null, 5)).hasSize(5);
    }
}
