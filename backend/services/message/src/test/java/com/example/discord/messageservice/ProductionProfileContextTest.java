package com.example.discord.messageservice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(classes = MessageServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration,org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
        "spring.flyway.enabled=false",
        "spring.kafka.listener.auto-startup=false",
        "discord.auth.jwt.issuer=discord-identity",
        "discord.auth.jwt.audience=discord-api",
        "discord.auth.jwt.key-id=profile-test",
        "discord.auth.jwt.public-key-locations.profile-test=file:../../modules/identity/src/test/resources/identity/ed25519-public.pem"
    })
@ActiveProfiles({"postgres", "kafka"})
class ProductionProfileContextTest {
    @MockitoBean
    private JdbcTemplate jdbc;
    @MockitoBean
    private org.springframework.scheduling.TaskScheduler taskScheduler;

    @Autowired
    private ApplicationContext context;

    @Test
    void wiresProductionKafkaWithoutExternalServices() {
        assertThat(context.getBean(KafkaTemplate.class)).isNotNull();
        assertThat(context.containsBean("kafkaListenerContainerFactory")).isTrue();
        assertThat(context.getBean(AuthorizationProjectionKafkaConsumer.class)).isNotNull();
        var containers = context.getBean(KafkaListenerEndpointRegistry.class).getListenerContainers();
        assertThat(containers).hasSize(1);
        assertThat(containers).allSatisfy(container -> assertThat(container.isRunning()).isFalse());
        assertThat(context.getBean(com.example.discord.message.MessagePublishedDispatcher.class))
            .isInstanceOf(KafkaMessagePublishedDispatcher.class);
        assertThat(context.getBean(com.example.discord.message.MessagePublicationRelay.class)).isNotNull();
    }
}
