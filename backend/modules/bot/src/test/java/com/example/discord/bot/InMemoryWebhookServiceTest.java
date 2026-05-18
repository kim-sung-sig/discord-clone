package com.example.discord.bot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryWebhookServiceTest {
    private static final UUID GUILD_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CHANNEL_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void createsWebhookWithOneTimeTokenAndStoresMetadataWithoutToken() {
        InMemoryWebhookService service = new InMemoryWebhookService();

        CreatedWebhook created = service.createWebhook(GUILD_ID, CHANNEL_ID, ACTOR_ID, "deploy-bot", true);
        Webhook metadata = service.webhook(created.webhook().id());

        assertThat(created.token()).isNotBlank();
        assertThat(metadata.id()).isEqualTo(created.webhook().id());
        assertThat(metadata.name()).isEqualTo("deploy-bot");
        assertThat(metadata).hasNoNullFieldsOrPropertiesExcept();
        assertThat(service.auditEvents()).extracting(WebhookAuditEvent::action).containsExactly(WebhookAuditAction.WEBHOOK_CREATED);
    }

    @Test
    void rejectsWebhookCreationWithoutManagePermission() {
        InMemoryWebhookService service = new InMemoryWebhookService();

        assertThatThrownBy(() -> service.createWebhook(GUILD_ID, CHANNEL_ID, ACTOR_ID, "deploy-bot", false))
            .isInstanceOf(SecurityException.class)
            .hasMessage("webhook management permission is required");
    }

    @Test
    void sendsWebhookMessageWithExplicitSourceWhenTokenIsValid() {
        InMemoryWebhookService service = new InMemoryWebhookService();
        CreatedWebhook created = service.createWebhook(GUILD_ID, CHANNEL_ID, ACTOR_ID, "deploy-bot", true);

        WebhookMessage message = service.sendWebhook(created.webhook().id(), created.token(), "deployed", true);

        assertThat(message.source()).isEqualTo(WebhookMessageSource.WEBHOOK);
        assertThat(message.actorLabel()).isEqualTo("webhook:deploy-bot");
        assertThat(service.auditEvents()).extracting(WebhookAuditEvent::action)
            .containsExactly(WebhookAuditAction.WEBHOOK_SENT, WebhookAuditAction.WEBHOOK_CREATED);
    }

    @Test
    void rejectsWebhookSendWithInvalidTokenOrPermission() {
        InMemoryWebhookService service = new InMemoryWebhookService();
        CreatedWebhook created = service.createWebhook(GUILD_ID, CHANNEL_ID, ACTOR_ID, "deploy-bot", true);

        assertThatThrownBy(() -> service.sendWebhook(created.webhook().id(), "wrong-token", "deployed", true))
            .isInstanceOf(SecurityException.class)
            .hasMessage("webhook token is invalid");
        assertThatThrownBy(() -> service.sendWebhook(created.webhook().id(), created.token(), "deployed", false))
            .isInstanceOf(SecurityException.class)
            .hasMessage("webhook send permission is required");
    }
}
