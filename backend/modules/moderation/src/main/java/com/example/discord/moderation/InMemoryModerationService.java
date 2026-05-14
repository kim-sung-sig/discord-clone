package com.example.discord.moderation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class InMemoryModerationService {
    private final Map<UUID, List<OnboardingQuestion>> onboardingQuestionsByGuild = new LinkedHashMap<>();
    private final Map<UUID, List<AutoModRule>> autoModRulesByGuild = new LinkedHashMap<>();
    private final Map<UUID, List<AuditLogEntry>> auditLogsByGuild = new LinkedHashMap<>();

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
        List<AuditLogEntry> entries = new ArrayList<>(auditLogsByGuild.getOrDefault(guildId, List.of()));
        entries.sort((left, right) -> right.createdAt().compareTo(left.createdAt()));
        return List.copyOf(entries);
    }

    public synchronized void appendAudit(UUID guildId, AuditLogAction action, UUID actorId, UUID targetId, String reason) {
        require(guildId, "guildId");
        require(actorId, "actorId");
        require(targetId, "targetId");
        if (action == null) {
            throw new IllegalArgumentException("action is required");
        }
        AuditLogEntry entry = new AuditLogEntry(UUID.randomUUID(), guildId, action, actorId, targetId, reason, Instant.now());
        auditLogsByGuild.computeIfAbsent(guildId, ignored -> new ArrayList<>()).add(entry);
    }

    private static boolean containsKeyword(String normalizedContent, List<String> keywords) {
        return keywords.stream().anyMatch(normalizedContent::contains);
    }

    private static void require(UUID value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
