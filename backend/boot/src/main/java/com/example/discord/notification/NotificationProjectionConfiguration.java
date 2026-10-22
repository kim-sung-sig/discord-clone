package com.example.discord.notification;

import com.example.discord.guild.GuildService;
import com.example.discord.message.MessageLookupPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@Profile("postgres")
class NotificationProjectionConfiguration {
    @Bean
    MessageMentionProjector messageMentionProjector(
        MessageLookupPort messages,
        GuildService guilds,
        JdbcTemplate jdbc,
        JdbcNotificationInboxService inbox
    ) {
        return new MessageMentionProjector(
            messages,
            new MessageMentionRecipientResolver(guilds),
            messageId -> jdbc.queryForObject("SELECT sequence FROM messages WHERE id = ?", Long.class, messageId),
            inbox::recordMention
        );
    }
}
