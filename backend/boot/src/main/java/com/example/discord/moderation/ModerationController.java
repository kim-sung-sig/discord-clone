package com.example.discord.moderation;

import com.example.discord.auth.AuthenticatedUserResolver;
import com.example.discord.guild.GuildMember;
import com.example.discord.guild.InMemoryGuildService;
import com.example.discord.message.ChannelMessageTarget;
import com.example.discord.message.MessageLookupPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/guilds/{guildId}")
class ModerationController {
    private final InMemoryModerationService moderationService;
    private final InMemoryGuildService guildService;
    private final MessageLookupPort messages;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    ModerationController(
        InMemoryModerationService moderationService,
        InMemoryGuildService guildService,
        MessageLookupPort messages,
        AuthenticatedUserResolver authenticatedUserResolver
    ) {
        this.moderationService = moderationService;
        this.guildService = guildService;
        this.messages = messages;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @PostMapping("/onboarding/questions")
    ResponseEntity<OnboardingQuestionResponse> createOnboardingQuestion(
        @PathVariable UUID guildId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestBody CreateOnboardingQuestionRequest request
    ) {
        UUID requesterId = requireManageRoles(guildId, authorization);
        requireRequest(request);
        List<OnboardingAnswer> answers = request.answers().stream()
            .map(answer -> {
                List<UUID> roleGrantIds = answer.roleGrantIds() == null ? List.of() : answer.roleGrantIds();
                guildService.requireRoles(guildId, roleGrantIds);
                if (!guildService.canAssignRoles(guildId, requesterId, roleGrantIds)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "cannot configure role grants above requester");
                }
                return new OnboardingAnswer(UUID.randomUUID(), answer.label(), roleGrantIds);
            })
            .toList();

        OnboardingQuestion question = moderationService.createOnboardingQuestion(
            guildId,
            request.prompt(),
            answers,
            requesterId
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(OnboardingQuestionResponse.from(question));
    }

    @PostMapping("/onboarding/answers")
    MemberRoleResponse submitOnboardingAnswers(
        @PathVariable UUID guildId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestBody SubmitOnboardingAnswersRequest request
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        requireRequest(request);
        if (!guildService.isGuildMemberOrOwner(guildId, requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "guild member required");
        }
        GuildMember member = guildService.guild(guildId).member(requesterId);
        for (UUID roleId : moderationService.submitOnboardingAnswers(guildId, requesterId, request.answerIds())) {
            member = guildService.assignRoleToMember(guildId, requesterId, roleId);
        }
        return MemberRoleResponse.from(member);
    }

    @PostMapping("/automod/rules")
    ResponseEntity<AutoModRuleResponse> createAutoModRule(
        @PathVariable UUID guildId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestBody CreateAutoModRuleRequest request
    ) {
        UUID requesterId = requireManageMessages(guildId, authorization);
        requireRequest(request);
        AutoModRule rule = moderationService.createAutoModRule(
            guildId,
            requesterId,
            request.type(),
            request.name(),
            request.keywords()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(AutoModRuleResponse.from(rule));
    }

    @GetMapping("/audit-logs")
    List<AuditLogEntryResponse> auditLogs(
        @PathVariable UUID guildId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestParam(required = false) AuditLogAction action,
        @RequestParam(required = false) UUID actorId,
        @RequestParam(required = false) UUID targetId,
        @RequestParam(defaultValue = "50") int limit
    ) {
        requireManageMessages(guildId, authorization);
        return moderationService.auditLogs(guildId, action, actorId, targetId, limit).stream()
            .map(AuditLogEntryResponse::from)
            .toList();
    }

    @GetMapping("/security-alerts")
    List<SecurityAlertResponse> securityAlerts(
        @PathVariable UUID guildId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestParam(defaultValue = "50") int limit
    ) {
        requireManageMessages(guildId, authorization);
        return moderationService.securityAlerts(guildId, limit).stream()
            .map(SecurityAlertResponse::from)
            .toList();
    }

    @PostMapping("/channels/{channelId}/messages/{messageId}/reports")
    ResponseEntity<MessageReportResponse> reportMessage(
        @PathVariable UUID guildId,
        @PathVariable UUID channelId,
        @PathVariable UUID messageId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestBody ReportMessageRequest request
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        requireRequest(request);
        if (!guildService.canViewChannel(guildId, channelId, requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "view channel permission required");
        }
        messages.requireMessage(new ChannelMessageTarget(guildId, channelId), messageId);
        MessageReport report = moderationService.reportMessage(new ReportMessageCommand(
            guildId,
            channelId,
            messageId,
            requesterId,
            request.reason()
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(MessageReportResponse.from(report));
    }

    @GetMapping("/message-reports")
    List<MessageReportResponse> messageReports(
        @PathVariable UUID guildId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestParam(defaultValue = "50") int limit
    ) {
        requireManageMessages(guildId, authorization);
        return moderationService.pendingMessageReports(guildId, limit).stream()
            .map(MessageReportResponse::from)
            .toList();
    }

    @PostMapping("/message-reports/{reportId}/resolve")
    MessageReportResponse resolveMessageReport(
        @PathVariable UUID guildId,
        @PathVariable UUID reportId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestBody ResolveMessageReportRequest request
    ) {
        UUID requesterId = requireManageMessages(guildId, authorization);
        requireRequest(request);
        return MessageReportResponse.from(moderationService.resolveMessageReport(
            guildId,
            reportId,
            requesterId,
            request.status(),
            request.resolution()
        ));
    }

    private UUID requireManageRoles(UUID guildId, String authorization) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        if (!guildService.canManageRoles(guildId, requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "manage roles permission required");
        }
        return requesterId;
    }

    private UUID requireManageMessages(UUID guildId, String authorization) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        if (!canManageGuildMessages(guildId, requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "manage messages permission required");
        }
        return requesterId;
    }

    private boolean canManageGuildMessages(UUID guildId, UUID requesterId) {
        if (guildService.guild(guildId).ownerId().equals(requesterId)) {
            return true;
        }
        return guildService.visibleChannels(guildId, requesterId).stream()
            .anyMatch(channel -> guildService.canManageMessages(guildId, channel.id(), requesterId));
    }

    private static void requireRequest(Object request) {
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
    }

    record CreateOnboardingQuestionRequest(String prompt, List<CreateOnboardingAnswerRequest> answers) {
        CreateOnboardingQuestionRequest {
            answers = answers == null ? List.of() : answers;
        }
    }

    record CreateOnboardingAnswerRequest(String label, List<UUID> roleGrantIds) {
    }

    record SubmitOnboardingAnswersRequest(List<UUID> answerIds) {
    }

    record CreateAutoModRuleRequest(AutoModRuleType type, String name, List<String> keywords) {
    }

    record ReportMessageRequest(String reason) {
    }

    record ResolveMessageReportRequest(MessageReportStatus status, String resolution) {
    }

    record OnboardingQuestionResponse(UUID id, UUID guildId, String prompt, List<OnboardingAnswerResponse> answers) {
        static OnboardingQuestionResponse from(OnboardingQuestion question) {
            return new OnboardingQuestionResponse(
                question.id(),
                question.guildId(),
                question.prompt(),
                question.answers().stream().map(OnboardingAnswerResponse::from).toList()
            );
        }
    }

    record OnboardingAnswerResponse(UUID id, String label, List<UUID> roleGrantIds) {
        static OnboardingAnswerResponse from(OnboardingAnswer answer) {
            return new OnboardingAnswerResponse(answer.id(), answer.label(), answer.roleGrantIds());
        }
    }

    record MemberRoleResponse(UUID memberId, List<UUID> roleIds) {
        static MemberRoleResponse from(GuildMember member) {
            return new MemberRoleResponse(member.userId(), List.copyOf(member.roleIds()));
        }
    }

    record AutoModRuleResponse(UUID id, UUID guildId, AutoModRuleType type, String name, List<String> keywords, boolean enabled) {
        static AutoModRuleResponse from(AutoModRule rule) {
            return new AutoModRuleResponse(rule.id(), rule.guildId(), rule.type(), rule.name(), rule.keywords(), rule.enabled());
        }
    }

    record AuditLogEntryResponse(
        UUID id,
        UUID guildId,
        AuditLogAction action,
        UUID actorId,
        UUID targetId,
        String reason,
        Instant createdAt
    ) {
        static AuditLogEntryResponse from(AuditLogEntry entry) {
            return new AuditLogEntryResponse(
                entry.id(),
                entry.guildId(),
                entry.action(),
                entry.actorId(),
                entry.targetId(),
                entry.reason(),
                entry.createdAt()
            );
        }
    }

    record SecurityAlertResponse(
        UUID id,
        UUID guildId,
        UUID actorId,
        UUID targetId,
        String type,
        String severity,
        String reason,
        Instant createdAt
    ) {
        static SecurityAlertResponse from(SecurityAlert alert) {
            return new SecurityAlertResponse(
                alert.id(),
                alert.guildId(),
                alert.actorId(),
                alert.targetId(),
                alert.type(),
                alert.severity(),
                alert.reason(),
                alert.createdAt()
            );
        }
    }

    record MessageReportResponse(
        UUID id,
        UUID guildId,
        UUID channelId,
        UUID messageId,
        UUID reporterId,
        String reason,
        MessageReportStatus status,
        UUID moderatorId,
        String resolution,
        Instant createdAt,
        Instant updatedAt
    ) {
        static MessageReportResponse from(MessageReport report) {
            Optional<UUID> moderatorId = report.moderatorId();
            return new MessageReportResponse(
                report.id(),
                report.guildId(),
                report.channelId(),
                report.messageId(),
                report.reporterId(),
                report.reason(),
                report.status(),
                moderatorId.orElse(null),
                report.resolution(),
                report.createdAt(),
                report.updatedAt()
            );
        }
    }

    record ErrorResponse(String message) {
    }
}

@RestControllerAdvice(assignableTypes = ModerationController.class)
class ModerationControllerAdvice {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ModerationController.ErrorResponse> invalidRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new ModerationController.ErrorResponse("invalid request"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ModerationController.ErrorResponse> unreadableRequest(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(new ModerationController.ErrorResponse("invalid request"));
    }
}
