package com.example.discord.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "DISCORD_RUN_POSTGRES_TESTS", matches = "true")
class PostgresModerationServiceContractTest {
    private DataSource dataSource;

    private PGSimpleDataSource database(String app) {
        var postgres = new PGSimpleDataSource();
        postgres.setUrl(System.getenv("POSTGRES_JDBC_URL"));
        postgres.setUser(System.getenv().getOrDefault("POSTGRES_USER", "dev_user"));
        postgres.setPassword(System.getenv().getOrDefault("POSTGRES_PASSWORD", "dev_password"));
        postgres.setApplicationName(app);
        return postgres;
    }

    @BeforeAll
    void migrateOnlyTaskDatabase() throws Exception {
        dataSource = database("moderation-contract");
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT current_database()")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).startsWith("discord_runtime_guild_");
        }
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    }

    @Test
    void questionsAnswersAndConfiguredRoleOrderSurviveFreshAdaptersAndGuildIsolation() throws Exception {
        var f = fixture();
        var other = fixture();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var answers = List.of(new OnboardingAnswer(UUID.randomUUID(), "a", List.of(first, second)),
            new OnboardingAnswer(UUID.randomUUID(), "b", List.of(second)));
        var question = service().createOnboardingQuestion(f.guild(), "question", answers, f.actor());
        var empty = service().createOnboardingQuestion(f.guild(), "empty", List.of(), f.actor());
        assertThat(service().onboardingQuestions(f.guild())).containsExactly(question, empty);
        assertThat(service().onboardingQuestions(other.guild())).isEmpty();
        assertThat(service().submitOnboardingAnswers(f.guild(), f.actor(),
            List.of(answers.getLast().id(), answers.getFirst().id(), UUID.randomUUID()))).containsExactly(first, second);
        assertThat(service().submitOnboardingAnswers(other.guild(), other.actor(), List.of(answers.getFirst().id()))).isEmpty();
        assertThat(service().auditLogs(f.guild()).getFirst().action()).isEqualTo(AuditLogAction.ONBOARDING_ANSWER_SUBMITTED);
    }

    @Test
    void rulesAndFirstMatchingDecisionAndAlertAuditAreDurableAndIsolated() throws Exception {
        var f = fixture();
        var other = fixture();
        var first = service().createAutoModRule(f.guild(), f.actor(), AutoModRuleType.KEYWORD, "first", List.of(" Spoiler "));
        service().createAutoModRule(f.guild(), f.actor(), AutoModRuleType.KEYWORD, "second", List.of("spoiler"));
        assertThat(service().autoModRules(f.guild()).getFirst()).isEqualTo(first);
        assertThat(service().evaluateMessage(f.guild(), f.channel(), f.actor(), "SPOILER").ruleId()).contains(first.id());
        assertThat(service().securityAlerts(f.guild())).hasSize(1);
        assertThat(service().auditLogs(f.guild()).getFirst().action()).isEqualTo(AuditLogAction.AUTOMOD_MESSAGE_BLOCKED);
        assertThat(service().evaluateMessage(other.guild(), other.channel(), other.actor(), "spoiler").blocked()).isFalse();
        assertThat(service().securityAlerts(other.guild())).isEmpty();
        execute("UPDATE moderation_rules SET enabled=FALSE WHERE guild_id=?", f.guild());
        assertThat(service().evaluateMessage(f.guild(), f.channel(), f.actor(), "spoiler").blocked()).isFalse();
    }

    @Test
    void reportsKeepCreationOrderAndResolutionAndRejectOtherGuildAndInvalidStatus() throws Exception {
        var f = fixture();
        var other = fixture();
        UUID message = UUID.randomUUID();
        var first = service().reportMessage(new ReportMessageCommand(f.guild(), f.channel(), message, f.actor(), "one"));
        var second = service().reportMessage(new ReportMessageCommand(f.guild(), f.channel(), message, f.actor(), "two"));
        assertThat(service().pendingMessageReports(f.guild())).extracting(MessageReport::id).containsExactly(first.id(), second.id());
        assertThatThrownBy(() -> service().resolveMessageReport(other.guild(), first.id(), other.actor(), MessageReportStatus.RESOLVED, "no"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service().resolveMessageReport(f.guild(), first.id(), f.actor(), MessageReportStatus.OPEN, "no"))
            .isInstanceOf(IllegalArgumentException.class);
        var resolved = service().resolveMessageReport(f.guild(), first.id(), f.actor(), MessageReportStatus.RESOLVED, "done");
        assertThat(service().messageReports(f.guild(), message).getFirst()).isEqualTo(resolved);
        assertThat(service().pendingMessageReports(f.guild())).extracting(MessageReport::id).containsExactly(second.id());
        assertThat(service().pendingMessageReports(other.guild())).isEmpty();
    }

    @Test
    void filtersPageBoundsAndAllRetainedLogsStayWithinFiveHundred() throws Exception {
        var f = fixture();
        UUID message = UUID.randomUUID();
        execute("""
            INSERT INTO moderation_message_reports(id,guild_id,channel_id,message_id,reporter_id,reason,status,created_at,updated_at)
            SELECT gen_random_uuid(),?,?,?,?,'seed','OPEN',now(),now() FROM generate_series(1,500)
            """, f.guild(), f.channel(), message, f.actor());
        execute("""
            INSERT INTO moderation_security_alerts(id,guild_id,actor_id,target_id,type,severity,reason)
            SELECT gen_random_uuid(),?,?,?,'TEST','MEDIUM','seed' FROM generate_series(1,500)
            """, f.guild(), f.actor(), f.channel());
        service().reportMessage(new ReportMessageCommand(f.guild(), f.channel(), message, f.actor(), "new"));
        var rule = service().createAutoModRule(f.guild(), f.actor(), AutoModRuleType.KEYWORD, "retention", List.of("block"));
        service().evaluateMessage(f.guild(), f.channel(), f.actor(), "block");
        assertThat(count("SELECT count(*) FROM moderation_message_reports WHERE guild_id=?", f.guild())).isEqualTo(500);
        assertThat(count("SELECT count(*) FROM moderation_security_alerts WHERE guild_id=?", f.guild())).isEqualTo(500);
        assertThat(service().pendingMessageReports(f.guild())).hasSize(50);
        assertThat(service().pendingMessageReports(f.guild(), -1)).hasSize(50);
        assertThat(service().messageReports(f.guild(), message, 1000)).hasSize(100);
        assertThat(service().securityAlerts(f.guild(), 0)).hasSize(50);
        assertThat(service().securityAlerts(f.guild(), 101)).hasSize(100);
        assertThat(service().auditLogs(f.guild(), AuditLogAction.AUTOMOD_MESSAGE_BLOCKED, f.actor(), rule.id(), 100))
            .hasSize(1);
        assertThat(service().auditLogs(f.guild(), null, UUID.randomUUID(), null, 100)).isEmpty();
        assertThat(service().auditLogs(f.guild(), null, null, UUID.randomUUID(), 100)).isEmpty();
    }

    @Test
    void guildLockWaitIsObservedAndConcurrentAuditRetentionAndTimestampsAreStrict() throws Exception {
        var f = fixture();
        execute("""
            INSERT INTO moderation_audit_logs(id,guild_id,action,actor_id,target_id,reason,created_at)
            SELECT gen_random_uuid(),?,'MESSAGE_REPORTED',?,?,'seed',clock_timestamp()+interval '1 day'
            FROM generate_series(1,499)
            """, f.guild(), f.actor(), f.channel());
        String app = "moderation-lock-" + UUID.randomUUID();
        var concurrent = new JdbcModerationService(database(app));
        try (Connection held = dataSource.getConnection(); var executor = Executors.newFixedThreadPool(2)) {
            held.setAutoCommit(false);
            try (var statement = held.prepareStatement("SELECT id FROM guilds WHERE id=? FOR UPDATE")) {
                statement.setObject(1, f.guild());
                try (var rows = statement.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                }
            }
            var first = executor.submit(() -> concurrent.appendAudit(f.guild(), AuditLogAction.ROLE_ASSIGNED, f.actor(), f.channel(), "one"));
            var second = executor.submit(() -> concurrent.appendAudit(f.guild(), AuditLogAction.ROLE_ASSIGNED, f.actor(), f.channel(), "two"));
            try {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (count("SELECT count(*) FROM pg_stat_activity WHERE application_name=? AND wait_event_type='Lock'", app) < 2
                    && System.nanoTime() < deadline) {
                    Thread.sleep(20);
                }
                assertThat(count("SELECT count(*) FROM pg_stat_activity WHERE application_name=? AND wait_event_type='Lock'", app)).isEqualTo(2);
                assertThat(first.isDone()).isFalse();
                assertThat(second.isDone()).isFalse();
            } finally {
                held.commit();
            }
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        }
        assertThat(count("SELECT count(*) FROM moderation_audit_logs WHERE guild_id=?", f.guild())).isEqualTo(500);
        var latest = service().auditLogs(f.guild(), null, null, null, 3);
        assertThat(latest.get(0).createdAt()).isAfter(latest.get(1).createdAt());
        assertThat(latest.get(1).createdAt()).isAfter(latest.get(2).createdAt());
        assertThat(service().auditLogs(f.guild(), null, null, null, 0)).hasSize(50);
        assertThat(service().auditLogs(f.guild(), null, null, null, 999)).hasSize(100);
    }

    @Test
    void auditFailureRollsBackRuleAndReportAndResolveAndRecovers() throws Exception {
        var f = fixture();
        var report = service().reportMessage(new ReportMessageCommand(f.guild(), f.channel(), UUID.randomUUID(), f.actor(), "before"));
        try (var failure = failInserts("moderation_audit_logs", f.guild())) {
            assertThatThrownBy(() -> service().createAutoModRule(f.guild(), f.actor(), AutoModRuleType.KEYWORD, "failed", List.of("x")))
                .isInstanceOf(IllegalStateException.class);
            assertThat(service().autoModRules(f.guild())).isEmpty();
            assertThatThrownBy(() -> service().reportMessage(new ReportMessageCommand(f.guild(), f.channel(), UUID.randomUUID(), f.actor(), "failed")))
                .isInstanceOf(IllegalStateException.class);
            assertThat(service().pendingMessageReports(f.guild())).hasSize(1);
            assertThatThrownBy(() -> service().resolveMessageReport(f.guild(), report.id(), f.actor(), MessageReportStatus.RESOLVED, "failed"))
                .isInstanceOf(IllegalStateException.class);
            assertThat(service().pendingMessageReports(f.guild()).getFirst().status()).isEqualTo(MessageReportStatus.OPEN);
        }
        service().resolveMessageReport(f.guild(), report.id(), f.actor(), MessageReportStatus.RESOLVED, "recovered");
        assertThat(service().pendingMessageReports(f.guild())).isEmpty();
    }

    @Test
    void alertOrAuditFailureCannotLeavePartialAutoModState() throws Exception {
        var f = fixture();
        service().createAutoModRule(f.guild(), f.actor(), AutoModRuleType.KEYWORD, "rule", List.of("block"));
        for (String table : List.of("moderation_security_alerts", "moderation_audit_logs")) {
            try (var failure = failInserts(table, f.guild())) {
                assertThatThrownBy(() -> service().evaluateMessage(f.guild(), f.channel(), f.actor(), "block"))
                    .isInstanceOf(IllegalStateException.class);
                assertThat(service().securityAlerts(f.guild())).isEmpty();
                assertThat(service().auditLogs(f.guild())).hasSize(1);
            }
        }
        assertThat(service().evaluateMessage(f.guild(), f.channel(), f.actor(), "block").blocked()).isTrue();
        assertThat(service().securityAlerts(f.guild())).hasSize(1);
    }

    @Test
    void answerFailureRollsBackTheQuestionAndEarlierAnswer() throws Exception {
        var f = fixture();
        var answers = List.of(new OnboardingAnswer(UUID.randomUUID(), "one", List.of()),
            new OnboardingAnswer(UUID.randomUUID(), "two", List.of(UUID.randomUUID())));
        try (var failure = failInserts("moderation_answers", f.guild())) {
            assertThatThrownBy(() -> service().createOnboardingQuestion(f.guild(), "failed", answers, f.actor()))
                .isInstanceOf(IllegalStateException.class);
            assertThat(service().onboardingQuestions(f.guild())).isEmpty();
            assertThat(count("SELECT count(*) FROM moderation_answers a JOIN moderation_questions q ON q.id=a.question_id WHERE q.guild_id=?", f.guild()))
                .isZero();
        }
        service().createOnboardingQuestion(f.guild(), "recovered", answers, f.actor());
        assertThat(service().onboardingQuestions(f.guild()).getFirst().answers()).isEqualTo(answers);
    }

    private AutoCloseable failInserts(String table, UUID guild) throws Exception {
        String function = "moderation_fail_" + UUID.randomUUID().toString().replace("-", "");
        String trigger = function + "_tr";
        String condition = table.equals("moderation_answers")
            ? "NEW.ordinal=1 AND EXISTS (SELECT 1 FROM moderation_questions WHERE id=NEW.question_id AND guild_id='" + guild + "')"
            : "NEW.guild_id='" + guild + "'";
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE FUNCTION " + function + "() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN IF "
                + condition + " THEN RAISE EXCEPTION 'test moderation failure'; END IF; RETURN NEW; END; $$");
            statement.execute("CREATE TRIGGER " + trigger + " BEFORE INSERT ON " + table + " FOR EACH ROW EXECUTE FUNCTION " + function + "()");
        }
        return () -> {
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                statement.execute("DROP TRIGGER " + trigger + " ON " + table);
                statement.execute("DROP FUNCTION " + function + "()");
            }
        };
    }

    private JdbcModerationService service() {
        return new JdbcModerationService(dataSource);
    }

    private Fixture fixture() throws Exception {
        UUID actor = UUID.randomUUID();
        UUID guild = UUID.randomUUID();
        execute("INSERT INTO users(id,username,display_name) VALUES (?,?,'Moderation Test')", actor, actor.toString().replace("-", ""));
        execute("INSERT INTO guilds(id,name,owner_id) VALUES (?,'moderation-test',?)", guild, actor);
        return new Fixture(guild, actor, UUID.randomUUID());
    }

    private void execute(String sql, Object... args) throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            statement.executeUpdate();
        }
    }

    private long count(String sql, Object... args) throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getLong(1);
            }
        }
    }

    private record Fixture(UUID guild, UUID actor, UUID channel) {
    }
}
