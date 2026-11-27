package com.example.discord.websocketservice;

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

@SpringBootTest(classes = WebsocketServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration,org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
        "spring.flyway.enabled=false",
        "spring.kafka.listener.auto-startup=false",
        "discord.auth.jwt.issuer=discord-identity",
        "discord.auth.jwt.audience=discord-api",
        "discord.auth.jwt.key-id=profile-test",
        "discord.auth.jwt.public-key-locations.profile-test=classpath:identity/ed25519-public.pem",
        "POSTGRES_PASSWORD=synthetic-profile-binding"
    })
@ActiveProfiles({"postgres", "kafka"})
class ProductionProfileContextTest {
    @MockitoBean
    private JdbcTemplate jdbc;

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
    }

    @Test
    void bindsPostgresPasswordAtDatasourcePath() {
        var properties = org.springframework.boot.context.properties.bind.Binder.get(context.getEnvironment())
            .bind("spring.datasource", org.springframework.boot.jdbc.autoconfigure.DataSourceProperties.class).get();
        assertThat("synthetic-profile-binding".equals(properties.getPassword())).isTrue();
    }
}
