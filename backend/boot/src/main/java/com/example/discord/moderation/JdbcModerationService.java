package com.example.discord.moderation;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class JdbcModerationService implements ModerationService {
    private static final Logger log = LoggerFactory.getLogger(JdbcModerationService.class);
    private final DataSource dataSource;

    JdbcModerationService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public OnboardingQuestion createOnboardingQuestion(UUID guild, String prompt, List<OnboardingAnswer> answers, UUID actor) {
        require(actor, "actorId");
        var question = new OnboardingQuestion(UUID.randomUUID(), guild, prompt, answers);
        return write(guild, "question_create", connection -> {
            update(connection, "question_insert", "INSERT INTO moderation_questions(id,guild_id,prompt) VALUES (?,?,?)",
                question.id(), guild, prompt);
            for (int i = 0; i < question.answers().size(); i++) {
                OnboardingAnswer answer = question.answers().get(i);
                var roles = connection.createArrayOf("uuid", answer.roleGrantIds().toArray());
                try {
                    update(connection, "answer_insert", """
                        INSERT INTO moderation_answers(question_id,ordinal,id,label,role_ids) VALUES (?,?,?,?,?)
                        """, question.id(), i, answer.id(), answer.label(), roles);
                } finally {
                    roles.free();
                }
            }
            return question;
        });
    }

    @Override
    public List<UUID> submitOnboardingAnswers(UUID guild, UUID member, List<UUID> answerIds) {
        require(member, "memberId");
        return write(guild, "onboarding_submit", connection -> {
            var selected = new LinkedHashSet<>(answerIds == null ? List.of() : answerIds);
            var roles = new LinkedHashSet<UUID>();
            for (OnboardingQuestion question : questions(connection, guild)) {
                for (OnboardingAnswer answer : question.answers()) {
                    if (selected.contains(answer.id())) {
                        roles.addAll(answer.roleGrantIds());
                    }
                }
            }
            appendAudit(connection, guild, AuditLogAction.ONBOARDING_ANSWER_SUBMITTED, member, member, "onboarding answers submitted");
            return List.copyOf(roles);
        });
    }

    @Override
    public AutoModRule createAutoModRule(UUID guild, UUID actor, AutoModRuleType type, String name, List<String> keywords) {
        require(actor, "actorId");
        var rule = new AutoModRule(UUID.randomUUID(), guild, type, name, keywords, true);
        return write(guild, "rule_create", connection -> {
            var array = connection.createArrayOf("text", rule.keywords().toArray());
            try {
                update(connection, "rule_insert", """
                    INSERT INTO moderation_rules(id,guild_id,type,name,keywords,enabled) VALUES (?,?,?,?,?,?)
                    """, rule.id(), guild, type.name(), name, array, true);
            } finally {
                array.free();
            }
            appendAudit(connection, guild, AuditLogAction.AUTOMOD_RULE_CREATED, actor, rule.id(), "automod rule created");
            return rule;
        });
    }

    @Override
    public AutoModDecision evaluateMessage(UUID guild, UUID channel, UUID author, String content) {
        require(channel, "channelId");
        require(author, "authorId");
        // Most messages match no rule, so decide them without the guild row lock that alert/audit retention needs.
        // A match is only a candidate: the block is decided again under the lock against the then-active rules.
        boolean candidate = read(guild, connection -> {
            requireGuild(connection, guild);
            return rules(connection, guild).stream().anyMatch(rule -> rule.matches(content));
        });
        if (!candidate) {
            return AutoModDecision.allowed();
        }
        return write(guild, "message_evaluate", connection -> {
            for (AutoModRule rule : rules(connection, guild)) {
                if (rule.matches(content)) {
                    update(connection, "alert_insert", """
                        INSERT INTO moderation_security_alerts(id,guild_id,actor_id,target_id,type,severity,reason)
                        VALUES (?,?,?,?,'AUTOMOD_BLOCK','MEDIUM','keyword automod block')
                        """, UUID.randomUUID(), guild, author, channel);
                    prune(connection, "moderation_security_alerts", guild);
                    appendAudit(connection, guild, AuditLogAction.AUTOMOD_MESSAGE_BLOCKED, author, rule.id(), "keyword automod block");
                    return AutoModDecision.blocked(rule.id(), "message blocked by AutoMod");
                }
            }
            return AutoModDecision.allowed();
        });
    }

    @Override
    public List<OnboardingQuestion> onboardingQuestions(UUID guild) {
        return read(guild, connection -> questions(connection, guild));
    }

    private static List<OnboardingQuestion> questions(Connection connection, UUID guild) throws SQLException {
        Map<UUID, OnboardingQuestion> questions = new LinkedHashMap<>();
        Map<UUID, List<OnboardingAnswer>> answers = new LinkedHashMap<>();
        try (var statement = connection.prepareStatement("""
            SELECT q.id,q.prompt,a.id AS answer_id,a.label,a.role_ids FROM moderation_questions q
            LEFT JOIN moderation_answers a ON a.question_id=q.id WHERE q.guild_id=? ORDER BY q.ordinal,a.ordinal
            """)) {
            statement.setObject(1, guild);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    UUID id = rows.getObject("id", UUID.class);
                    questions.putIfAbsent(id, new OnboardingQuestion(id, guild, rows.getString("prompt"), List.of()));
                    if (rows.getObject("answer_id") != null) {
                        var array = rows.getArray("role_ids");
                        try {
                            answers.computeIfAbsent(id, ignored -> new ArrayList<>()).add(new OnboardingAnswer(
                                rows.getObject("answer_id", UUID.class), rows.getString("label"), Arrays.asList((UUID[]) array.getArray())));
                        } finally {
                            array.free();
                        }
                    }
                }
            }
        }
        return questions.values().stream().map(q -> new OnboardingQuestion(q.id(), guild, q.prompt(),
            answers.getOrDefault(q.id(), List.of()))).toList();
    }

    @Override
    public List<AutoModRule> autoModRules(UUID guild) {
        return read(guild, connection -> rules(connection, guild));
    }

    private static List<AutoModRule> rules(Connection connection, UUID guild) throws SQLException {
        return query(connection, "SELECT * FROM moderation_rules WHERE guild_id=? ORDER BY ordinal", rows -> {
            var array = rows.getArray("keywords");
            try {
                return new AutoModRule(rows.getObject("id", UUID.class), guild, AutoModRuleType.valueOf(rows.getString("type")),
                    rows.getString("name"), Arrays.asList((String[]) array.getArray()), rows.getBoolean("enabled"));
            } finally {
                array.free();
            }
        }, guild);
    }

    @Override
    public List<AuditLogEntry> auditLogs(UUID guild) {
        return auditLogs(guild, null, null, null, 50);
    }

    @Override
    public List<AuditLogEntry> auditLogs(UUID guild, AuditLogAction action, UUID actor, UUID target) {
        return auditLogs(guild, action, actor, target, 50);
    }

    @Override
    public List<AuditLogEntry> auditLogs(UUID guild, AuditLogAction action, UUID actor, UUID target, int limit) {
        return read(guild, connection -> query(connection, """
            SELECT * FROM moderation_audit_logs WHERE guild_id=?
            AND (?::varchar IS NULL OR action=?) AND (?::uuid IS NULL OR actor_id=?) AND (?::uuid IS NULL OR target_id=?)
            ORDER BY ordinal DESC LIMIT ?
            """, rows -> new AuditLogEntry(rows.getObject("id", UUID.class), guild,
                AuditLogAction.valueOf(rows.getString("action")), rows.getObject("actor_id", UUID.class),
                rows.getObject("target_id", UUID.class), rows.getString("reason"), rows.getTimestamp("created_at").toInstant()),
            guild, action == null ? null : action.name(), action == null ? null : action.name(), actor, actor, target, target, pageSize(limit)));
    }

    @Override
    public List<SecurityAlert> securityAlerts(UUID guild) {
        return securityAlerts(guild, 50);
    }

    @Override
    public List<SecurityAlert> securityAlerts(UUID guild, int limit) {
        return read(guild, connection -> query(connection, """
            SELECT * FROM moderation_security_alerts WHERE guild_id=? ORDER BY ordinal DESC LIMIT ?
            """, rows -> new SecurityAlert(rows.getObject("id", UUID.class), guild, rows.getObject("actor_id", UUID.class),
                rows.getObject("target_id", UUID.class), rows.getString("type"), rows.getString("severity"),
                rows.getString("reason"), rows.getTimestamp("created_at").toInstant()), guild, pageSize(limit)));
    }

    @Override
    public MessageReport reportMessage(ReportMessageCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        Instant now = Instant.now();
        var report = new MessageReport(UUID.randomUUID(), command.guildId(), command.channelId(), command.messageId(),
            command.reporterId(), command.reason(), MessageReportStatus.OPEN, Optional.empty(), "", now, now);
        return write(command.guildId(), "report_create", connection -> {
            update(connection, "report_insert", """
                INSERT INTO moderation_message_reports(id,guild_id,channel_id,message_id,reporter_id,reason,status,created_at,updated_at)
                VALUES (?,?,?,?,?,?,'OPEN',?,?)
                """, report.id(), report.guildId(), report.channelId(), report.messageId(), report.reporterId(), report.reason(), now, now);
            prune(connection, "moderation_message_reports", report.guildId());
            appendAudit(connection, report.guildId(), AuditLogAction.MESSAGE_REPORTED, report.reporterId(), report.messageId(), "message reported");
            return query(connection, "SELECT * FROM moderation_message_reports WHERE guild_id=? AND id=?",
                JdbcModerationService::report, report.guildId(), report.id()).getFirst();
        });
    }

    @Override
    public MessageReport resolveMessageReport(UUID guild, UUID id, UUID moderator, MessageReportStatus status, String resolution) {
        require(id, "reportId");
        require(moderator, "moderatorId");
        return write(guild, "report_resolve", connection -> {
            var reports = query(connection, "SELECT * FROM moderation_message_reports WHERE guild_id=? AND id=? FOR UPDATE",
                JdbcModerationService::report, guild, id);
            if (reports.isEmpty()) {
                throw new IllegalArgumentException("report not found");
            }
            MessageReport updated = reports.getFirst().resolve(moderator, status, resolution, Instant.now());
            update(connection, "report_update", """
                UPDATE moderation_message_reports SET status=?,moderator_id=?,resolution=?,updated_at=? WHERE guild_id=? AND id=?
                """, updated.status().name(), moderator, updated.resolution(), updated.updatedAt(), guild, id);
            appendAudit(connection, guild, AuditLogAction.MESSAGE_REPORT_RESOLVED, moderator, updated.messageId(), "message report resolved");
            return query(connection, "SELECT * FROM moderation_message_reports WHERE guild_id=? AND id=?",
                JdbcModerationService::report, guild, id).getFirst();
        });
    }

    @Override
    public List<MessageReport> pendingMessageReports(UUID guild) {
        return pendingMessageReports(guild, 50);
    }

    @Override
    public List<MessageReport> pendingMessageReports(UUID guild, int limit) {
        return read(guild, connection -> query(connection, """
            SELECT * FROM moderation_message_reports WHERE guild_id=? AND status='OPEN' ORDER BY ordinal LIMIT ?
            """, JdbcModerationService::report, guild, pageSize(limit)));
    }

    @Override
    public List<MessageReport> messageReports(UUID guild, UUID message) {
        return messageReports(guild, message, 50);
    }

    @Override
    public List<MessageReport> messageReports(UUID guild, UUID message, int limit) {
        require(message, "messageId");
        return read(guild, connection -> query(connection, """
            SELECT * FROM moderation_message_reports WHERE guild_id=? AND message_id=? ORDER BY ordinal LIMIT ?
            """, JdbcModerationService::report, guild, message, pageSize(limit)));
    }

    private static MessageReport report(ResultSet rows) throws SQLException {
        return new MessageReport(rows.getObject("id", UUID.class), rows.getObject("guild_id", UUID.class),
            rows.getObject("channel_id", UUID.class), rows.getObject("message_id", UUID.class), rows.getObject("reporter_id", UUID.class),
            rows.getString("reason"), MessageReportStatus.valueOf(rows.getString("status")),
            Optional.ofNullable(rows.getObject("moderator_id", UUID.class)), rows.getString("resolution"),
            rows.getTimestamp("created_at").toInstant(), rows.getTimestamp("updated_at").toInstant());
    }

    @Override
    public void appendAudit(UUID guild, AuditLogAction action, UUID actor, UUID target, String reason) {
        require(actor, "actorId");
        require(target, "targetId");
        if (action == null) {
            throw new IllegalArgumentException("action is required");
        }
        write(guild, "audit_append", connection -> {
            appendAudit(connection, guild, action, actor, target, reason);
            return null;
        });
    }

    private static void appendAudit(Connection connection, UUID guild, AuditLogAction action, UUID actor, UUID target, String reason)
        throws SQLException {
        UUID id = UUID.randomUUID();
        update(connection, "audit_insert", """
            INSERT INTO moderation_audit_logs(id,guild_id,action,actor_id,target_id,reason,created_at)
            VALUES (?,?,?,?,?,?,GREATEST(clock_timestamp(),COALESCE(
                (SELECT MAX(created_at)+INTERVAL '1 microsecond' FROM moderation_audit_logs WHERE guild_id=?),
                '-infinity'::timestamptz)))
            """, id, guild, action.name(), actor, target, reason, guild);
        log.info("moderation_audit_staged id={} guildId={} action={} actorId={} targetId={} status=pending_commit",
            id, guild, action, actor, target);
        prune(connection, "moderation_audit_logs", guild);
    }

    private static void prune(Connection connection, String table, UUID guild) throws SQLException {
        // table is an internal constant at each caller; user values always remain bound parameters.
        update(connection, table + "_prune", "DELETE FROM " + table + " WHERE guild_id=? AND ordinal IN "
            + "(SELECT ordinal FROM " + table + " WHERE guild_id=? ORDER BY ordinal DESC OFFSET 500)", guild, guild);
    }

    private <T> T write(UUID guild, String operation, SqlWork<T> work) {
        require(guild, "guildId");
        log.info("moderation_started operation={} guildId={}", operation, guild);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (var statement = connection.prepareStatement("SELECT id FROM guilds WHERE id=? FOR UPDATE")) {
                    statement.setObject(1, guild);
                    try (var rows = statement.executeQuery()) {
                        if (!rows.next()) {
                            throw new IllegalArgumentException("guild not found");
                        }
                    }
                }
                T result = work.run(connection);
                connection.commit();
                UUID id = result instanceof OnboardingQuestion question ? question.id()
                    : result instanceof AutoModRule rule ? rule.id() : result instanceof MessageReport report ? report.id() : null;
                log.info("moderation_committed operation={} guildId={} entityId={} status=committed", operation, guild, id);
                return result;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                if (exception instanceof RuntimeException) {
                    log.warn("moderation_rejected operation={} guildId={} type={}", operation, guild, exception.getClass().getSimpleName());
                }
                throw exception;
            }
        } catch (SQLException exception) {
            log.warn("moderation_failed operation={} guildId={} sqlState={}", operation, guild, exception.getSQLState());
            throw new IllegalStateException("failed moderation operation: " + operation, exception);
        }
    }

    private <T> T read(UUID guild, SqlWork<T> work) {
        require(guild, "guildId");
        log.info("moderation_read_started guildId={}", guild);
        try (Connection connection = dataSource.getConnection()) {
            T result = work.run(connection);
            log.info("moderation_read_completed guildId={} rows={}", guild, result instanceof List<?> list ? list.size() : 1);
            return result;
        } catch (SQLException exception) {
            log.warn("moderation_read_failed guildId={} sqlState={}", guild, exception.getSQLState());
            throw new IllegalStateException("failed to read moderation state", exception);
        }
    }

    private static void requireGuild(Connection connection, UUID guild) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT 1 FROM guilds WHERE id=?")) {
            statement.setObject(1, guild);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new IllegalArgumentException("guild not found");
                }
            }
        }
    }

    private static int update(Connection connection, String operation, String sql, Object... args) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            bind(statement, args);
            int rows = statement.executeUpdate();
            log.info("moderation_sql_effect operation={} rows={} status=pending_commit", operation, rows);
            return rows;
        }
    }

    private static <T> List<T> query(Connection connection, String sql, RowMapper<T> mapper, Object... args) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            bind(statement, args);
            try (var rows = statement.executeQuery()) {
                List<T> result = new ArrayList<>();
                while (rows.next()) {
                    result.add(mapper.map(rows));
                }
                return List.copyOf(result);
            }
        }
    }

    private static void bind(java.sql.PreparedStatement statement, Object[] args) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            statement.setObject(i + 1, args[i] instanceof Instant instant ? Timestamp.from(instant) : args[i]);
        }
    }

    private static void require(UUID id, String field) {
        if (id == null) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private static int pageSize(int limit) {
        return limit < 1 ? 50 : Math.min(limit, 100);
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    private interface RowMapper<T> {
        T map(ResultSet rows) throws SQLException;
    }
}
