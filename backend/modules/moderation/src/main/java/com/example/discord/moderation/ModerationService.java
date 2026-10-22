package com.example.discord.moderation;

import java.util.List;
import java.util.UUID;

public interface ModerationService {
    OnboardingQuestion createOnboardingQuestion(
        UUID guildId,
        String prompt,
        List<OnboardingAnswer> answers,
        UUID actorId
    );
    List<UUID> submitOnboardingAnswers(UUID guildId, UUID memberId, List<UUID> answerIds);
    AutoModRule createAutoModRule(
        UUID guildId,
        UUID actorId,
        AutoModRuleType type,
        String name,
        List<String> keywords
    );
    AutoModDecision evaluateMessage(UUID guildId, UUID channelId, UUID authorId, String content);
    List<OnboardingQuestion> onboardingQuestions(UUID guildId);
    List<AutoModRule> autoModRules(UUID guildId);
    List<AuditLogEntry> auditLogs(UUID guildId);
    List<AuditLogEntry> auditLogs(
        UUID guildId,
        AuditLogAction action,
        UUID actorId,
        UUID targetId
    );
    List<AuditLogEntry> auditLogs(
        UUID guildId,
        AuditLogAction action,
        UUID actorId,
        UUID targetId,
        int limit
    );
    List<SecurityAlert> securityAlerts(UUID guildId);
    List<SecurityAlert> securityAlerts(UUID guildId, int limit);
    MessageReport reportMessage(ReportMessageCommand command);
    MessageReport resolveMessageReport(
        UUID guildId,
        UUID reportId,
        UUID moderatorId,
        MessageReportStatus status,
        String resolution
    );
    List<MessageReport> pendingMessageReports(UUID guildId);
    List<MessageReport> pendingMessageReports(UUID guildId, int limit);
    List<MessageReport> messageReports(UUID guildId, UUID messageId);
    List<MessageReport> messageReports(UUID guildId, UUID messageId, int limit);
    void appendAudit(UUID guildId, AuditLogAction action, UUID actorId, UUID targetId, String reason);
}
