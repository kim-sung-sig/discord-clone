package com.example.discord.moderation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class InMemoryModerationService {
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_AUDIT_LOGS_PER_GUILD = 500;
    private static final int MAX_SECURITY_ALERTS_PER_GUILD = 500;
    private static final int MAX_MESSAGE_REPORTS_PER_GUILD = 500;

    private final Map<UUID, List<OnboardingQuestion>> onboardingQuestionsByGuild = new LinkedHashMap<>();
    private final Map<UUID, List<AutoModRule>> autoModRulesByGuild = new LinkedHashMap<>();
    private final Map<UUID, List<AuditLogEntry>> auditLogsByGuild = new LinkedHashMap<>();
    private final Map<UUID, List<SecurityAlert>> securityAlertsByGuild = new LinkedHashMap<>();
    private final Map<UUID, List<MessageReport>> messageReportsByGuild = new LinkedHashMap<>();
    private Instant lastAuditCreatedAt = Instant.EPOCH;

    public synchronized OnboardingQuestion createOnboardingQuestion(
        UUID guildId,
        String prompt,
        List<OnboardingAnswer> answers,
        UUID actorId
    ) {
        require(guildId, "guildId");
        require(actorId, "actorId");
        OnboardingQuestion question = new OnboardingQuestion(UUID.randomUUID(), guildId, prompt, answers);
        onboardingQuestionsByGuild.computeIfAbsent(guildId, ignored -> new ArrayList<>()).add(question);
        return question;
    }

    public synchronized List<UUID> submitOnboardingAnswers(UUID guildId, UUID memberId, List<UUID> answerIds) {
        require(guildId, "guildId");
        require(memberId, "memberId");
        LinkedHashSet<UUID> selectedAnswerIds = new LinkedHashSet<>(answerIds == null ? List.of() : answerIds);
        LinkedHashSet<UUID> grantedRoleIds = new LinkedHashSet<>();

        for (OnboardingQuestion question : onboardingQuestionsByGuild.getOrDefault(guildId, List.of())) {
            for (OnboardingAnswer answer : question.answers()) {
                if (selectedAnswerIds.contains(answer.id())) {
                    grantedRoleIds.addAll(answer.roleGrantIds());
                }
            }
        }

        appendAudit(guildId, AuditLogAction.ONBOARDING_ANSWER_SUBMITTED, memberId, memberId, "onboarding answers submitted");
        return List.copyOf(grantedRoleIds);
    }

    public synchronized AutoModRule createAutoModRule(
        UUID guildId,
        UUID actorId,
        AutoModRuleType type,
        String name,
        List<String> keywords
    ) {
        require(guildId, "guildId");
        require(actorId, "actorId");
        AutoModRule rule = new AutoModRule(UUID.randomUUID(), guildId, type, name, keywords, true);
        autoModRulesByGuild.computeIfAbsent(guildId, ignored -> new ArrayList<>()).add(rule);
        appendAudit(guildId, AuditLogAction.AUTOMOD_RULE_CREATED, actorId, rule.id(), "automod rule created");
        return rule;
    }

    public synchronized AutoModDecision evaluateMessage(UUID guildId, UUID channelId, UUID authorId, String content) {
        require(guildId, "guildId");
        require(channelId, "channelId");
        require(authorId, "authorId");
        String normalizedContent = content == null ? "" : content.toLowerCase();

        for (AutoModRule rule : autoModRulesByGuild.getOrDefault(guildId, List.of())) {
            if (!rule.enabled()) {
                continue;
            }
            if (rule.type() == AutoModRuleType.KEYWORD && containsKeyword(normalizedContent, rule.keywords())) {
                appendSecurityAlert(guildId, authorId, channelId, "AUTOMOD_BLOCK", "MEDIUM", "keyword automod block");
                appendAudit(guildId, AuditLogAction.AUTOMOD_MESSAGE_BLOCKED, authorId, rule.id(), "keyword automod block");
                return AutoModDecision.blocked(rule.id(), "message blocked by AutoMod");
            }
        }
        return AutoModDecision.allowed();
    }

    public synchronized List<OnboardingQuestion> onboardingQuestions(UUID guildId) {
        return List.copyOf(onboardingQuestionsByGuild.getOrDefault(guildId, List.of()));
    }

    public synchronized List<AutoModRule> autoModRules(UUID guildId) {
        return List.copyOf(autoModRulesByGuild.getOrDefault(guildId, List.of()));
    }

    public synchronized List<AuditLogEntry> auditLogs(UUID guildId) {
        return auditLogs(guildId, null, null, null, DEFAULT_PAGE_SIZE);
    }

    public synchronized List<AuditLogEntry> auditLogs(
        UUID guildId,
        AuditLogAction action,
        UUID actorId,
        UUID targetId
    ) {
        return auditLogs(guildId, action, actorId, targetId, DEFAULT_PAGE_SIZE);
    }

    public synchronized List<AuditLogEntry> auditLogs(
        UUID guildId,
        AuditLogAction action,
        UUID actorId,
        UUID targetId,
        int limit
    ) {
        List<AuditLogEntry> entries = new ArrayList<>(auditLogsByGuild.getOrDefault(guildId, List.of()));
        entries.removeIf(entry -> action != null && entry.action() != action);
        entries.removeIf(entry -> actorId != null && !entry.actorId().equals(actorId));
        entries.removeIf(entry -> targetId != null && !entry.targetId().equals(targetId));
        entries.sort((left, right) -> right.createdAt().compareTo(left.createdAt()));
        return entries.stream().limit(pageSize(limit)).toList();
    }

    public synchronized List<SecurityAlert> securityAlerts(UUID guildId) {
        return securityAlerts(guildId, DEFAULT_PAGE_SIZE);
    }

    public synchronized List<SecurityAlert> securityAlerts(UUID guildId, int limit) {
        List<SecurityAlert> alerts = new ArrayList<>(securityAlertsByGuild.getOrDefault(guildId, List.of()));
        alerts.sort((left, right) -> right.createdAt().compareTo(left.createdAt()));
        return alerts.stream().limit(pageSize(limit)).toList();
    }

    public synchronized MessageReport reportMessage(ReportMessageCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        Instant now = Instant.now();
        MessageReport report = new MessageReport(
            UUID.randomUUID(),
            command.guildId(),
            command.channelId(),
            command.messageId(),
            command.reporterId(),
            command.reason(),
            MessageReportStatus.OPEN,
            Optional.empty(),
            "",
            now,
            now
        );
        appendBounded(
            messageReportsByGuild.computeIfAbsent(command.guildId(), ignored -> new ArrayList<>()),
            report,
            MAX_MESSAGE_REPORTS_PER_GUILD
        );
        appendAudit(command.guildId(), AuditLogAction.MESSAGE_REPORTED, command.reporterId(), command.messageId(), "message reported");
        return report;
    }

    public synchronized MessageReport resolveMessageReport(
        UUID guildId,
        UUID reportId,
        UUID moderatorId,
        MessageReportStatus status,
        String resolution
    ) {
        require(guildId, "guildId");
        require(reportId, "reportId");
        require(moderatorId, "moderatorId");
        if (status == null || status == MessageReportStatus.OPEN) {
            throw new IllegalArgumentException("terminal report status is required");
        }

        List<MessageReport> reports = messageReportsByGuild.getOrDefault(guildId, List.of());
        for (int i = 0; i < reports.size(); i++) {
            MessageReport current = reports.get(i);
            if (current.id().equals(reportId)) {
                MessageReport resolved = new MessageReport(
                    current.id(),
                    current.guildId(),
                    current.channelId(),
                    current.messageId(),
                    current.reporterId(),
                    current.reason(),
                    status,
                    Optional.of(moderatorId),
                    resolution,
                    current.createdAt(),
                    Instant.now()
                );
                reports.set(i, resolved);
                appendAudit(guildId, AuditLogAction.MESSAGE_REPORT_RESOLVED, moderatorId, current.messageId(), "message report resolved");
                return resolved;
            }
        }
        throw new IllegalArgumentException("report not found");
    }

    public synchronized List<MessageReport> pendingMessageReports(UUID guildId) {
        return pendingMessageReports(guildId, DEFAULT_PAGE_SIZE);
    }

    public synchronized List<MessageReport> pendingMessageReports(UUID guildId, int limit) {
        return messageReportsByGuild.getOrDefault(guildId, List.of()).stream()
            .filter(report -> report.status() == MessageReportStatus.OPEN)
            .limit(pageSize(limit))
            .toList();
    }

    public synchronized List<MessageReport> messageReports(UUID guildId, UUID messageId) {
        return messageReports(guildId, messageId, DEFAULT_PAGE_SIZE);
    }

    public synchronized List<MessageReport> messageReports(UUID guildId, UUID messageId, int limit) {
        require(guildId, "guildId");
        require(messageId, "messageId");
        return messageReportsByGuild.getOrDefault(guildId, List.of()).stream()
            .filter(report -> report.messageId().equals(messageId))
            .limit(pageSize(limit))
            .toList();
    }

    public synchronized void appendAudit(UUID guildId, AuditLogAction action, UUID actorId, UUID targetId, String reason) {
        require(guildId, "guildId");
        require(actorId, "actorId");
        require(targetId, "targetId");
        if (action == null) {
            throw new IllegalArgumentException("action is required");
        }
        AuditLogEntry entry = new AuditLogEntry(UUID.randomUUID(), guildId, action, actorId, targetId, reason, nextAuditInstant());
        appendBounded(
            auditLogsByGuild.computeIfAbsent(guildId, ignored -> new ArrayList<>()),
            entry,
            MAX_AUDIT_LOGS_PER_GUILD
        );
    }

    private Instant nextAuditInstant() {
        Instant now = Instant.now();
        if (!now.isAfter(lastAuditCreatedAt)) {
            now = lastAuditCreatedAt.plusNanos(1);
        }
        lastAuditCreatedAt = now;
        return now;
    }

    private void appendSecurityAlert(UUID guildId, UUID actorId, UUID targetId, String type, String severity, String reason) {
        SecurityAlert alert = new SecurityAlert(UUID.randomUUID(), guildId, actorId, targetId, type, severity, reason, Instant.now());
        appendBounded(
            securityAlertsByGuild.computeIfAbsent(guildId, ignored -> new ArrayList<>()),
            alert,
            MAX_SECURITY_ALERTS_PER_GUILD
        );
    }

    private static boolean containsKeyword(String normalizedContent, List<String> keywords) {
        return keywords.stream().anyMatch(normalizedContent::contains);
    }

    private static void require(UUID value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    private static int pageSize(int limit) {
        if (limit < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(limit, MAX_PAGE_SIZE);
    }

    private static <T> void appendBounded(List<T> entries, T entry, int maxEntries) {
        entries.add(entry);
        while (entries.size() > maxEntries) {
            entries.removeFirst();
        }
    }
}
