package com.amp.ops;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class RedisHealthIndicator implements HealthIndicator {

    private final RedisConnectionFactory connectionFactory;

    public RedisHealthIndicator(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Health health() {
        try {
            connectionFactory.getConnection().ping();
            return Health.up().withDetail("redis", "connected").build();
        } catch (Exception e) {
            return Health.down().withDetail("redis", e.getMessage()).build();
        }
    }
}
