package com.example.discord.messageservice;

import com.example.discord.permission.AuthzProjectionUpdated;
import com.example.discord.permission.AuthorizationDecision;
import com.example.discord.permission.AuthorizationProjection;
import com.example.discord.permission.AuthorizationProjectionStore;
import com.example.discord.permission.AuthorizationResourceType;
import com.example.discord.permission.AuthorizationWatermarkAdvanced;
import com.example.discord.permission.Permission;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("postgres")
public class JdbcAuthorizationProjectionStore implements AuthorizationProjectionStore {
    private static final String CONSUMER = "message-service";
    private final JdbcTemplate jdbc;

    public JdbcAuthorizationProjectionStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional
    public boolean apply(AuthzProjectionUpdated event) {
        int inserted = jdbc.update("INSERT INTO consumer_inbox(consumer_name,event_id,processed_at) VALUES (?,?,?) ON CONFLICT DO NOTHING",
            CONSUMER, event.eventId(), Timestamp.from(Instant.now()));
        if (inserted == 0) return false;
        jdbc.update("""
            INSERT INTO authorization_projection(guild_id,subject_id,resource_type,resource_id,permission_bits,permission_version,updated_at)
            VALUES (?,?,?,?,?,?,?)
            ON CONFLICT (guild_id,subject_id,resource_type,resource_id) DO UPDATE SET
              permission_bits=EXCLUDED.permission_bits, permission_version=EXCLUDED.permission_version, updated_at=EXCLUDED.updated_at
            WHERE authorization_projection.permission_version < EXCLUDED.permission_version
            """, event.guildId(), event.subjectId(), event.resourceType().name(), event.resourceId(), event.permissionBits(),
            event.permissionVersion(), Timestamp.from(event.occurredAt()));
        return true;
    }

    @Override
    @Transactional
    public boolean advanceWatermark(AuthorizationWatermarkAdvanced event) {
        int inserted = jdbc.update("INSERT INTO consumer_inbox(consumer_name,event_id,processed_at) VALUES (?,?,?) ON CONFLICT DO NOTHING",
            CONSUMER, event.eventId(), Timestamp.from(Instant.now()));
        if (inserted == 0) return false;
        jdbc.update("""
            INSERT INTO authorization_watermark(guild_id,permission_version,updated_at) VALUES (?,?,?)
            ON CONFLICT (guild_id) DO UPDATE SET permission_version=GREATEST(authorization_watermark.permission_version, EXCLUDED.permission_version), updated_at=EXCLUDED.updated_at
            """, event.guildId(), event.permissionVersion(), Timestamp.from(Instant.now()));
        return true;
    }

    @Override
    public AuthorizationDecision decide(UUID guildId, UUID subjectId, AuthorizationResourceType resourceType, UUID resourceId, Permission permission) {
        Long watermark = scalar("SELECT permission_version FROM authorization_watermark WHERE guild_id=?", guildId);
        if (watermark == null) return AuthorizationDecision.deny(AuthorizationDecision.Reason.MISSING_PROJECTION);
        try {
            AuthorizationProjection projection = jdbc.queryForObject("""
                SELECT guild_id,subject_id,resource_type,resource_id,permission_bits,permission_version
                FROM authorization_projection WHERE guild_id=? AND subject_id=? AND resource_type=? AND resource_id=?
                """, (ResultSet rs, int row) -> new AuthorizationProjection(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                    AuthorizationResourceType.valueOf(rs.getString(3)), rs.getObject(4, UUID.class), rs.getLong(5), rs.getLong(6), watermark),
                guildId, subjectId, resourceType.name(), resourceId);
            return projection.decide(permission);
        } catch (EmptyResultDataAccessException missing) {
            return AuthorizationDecision.deny(AuthorizationDecision.Reason.MISSING_PROJECTION);
        }
    }

    private Long scalar(String sql, UUID guildId) {
        try { return jdbc.queryForObject(sql, Long.class, guildId); }
        catch (EmptyResultDataAccessException missing) { return null; }
    }
}
