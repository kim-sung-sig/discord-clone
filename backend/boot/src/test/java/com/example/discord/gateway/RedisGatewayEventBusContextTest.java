package com.example.discord.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisGatewayEventBusContextTest {
    @Test
    void createsActualRedisComponentWithoutIo() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("redis");
            context.getBeanFactory().registerSingleton("redisTemplate", redis);
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.registerBean(Clock.class, Clock::systemUTC);
            context.register(RedisGatewayEventBus.class);
            context.refresh();
            assertThat(context.getBean(GatewayEventBus.class)).isInstanceOf(RedisGatewayEventBus.class);
            verifyNoInteractions(redis);
        }
    }

    @Test
    void excludesRedisBusWhenKafkaIsAlsoActive() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("redis", "kafka");
            context.register(RedisGatewayEventBus.class);
            context.refresh();
            assertThat(context.getBeansOfType(RedisGatewayEventBus.class)).isEmpty();
        }
    }
}
