package com.berk.courier_tracking_api.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class RedisLocationServiceImplTest {

    @Container
    static GenericContainer<?> redisContainer =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private RedisTemplate<String, Object> redisTemplate;
    private RedisLocationServiceImpl redisLocationService;

    @BeforeEach
    void setUp() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                redisContainer.getHost(), redisContainer.getMappedPort(6379));
        connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();

        redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        redisTemplate.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        redisTemplate.afterPropertiesSet();

        redisLocationService = new RedisLocationServiceImpl(redisTemplate);
        // nearbyLimit is normally @Value-injected; set manually without a Spring context.
        ReflectionTestUtils.setField(redisLocationService, "nearbyLimit", 20);
    }

    @AfterEach
    void tearDown() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        connectionFactory.destroy();
    }

    @Test
    void addCourierLocation_thenFindNearbyCouriers_shouldReturnAddedCourier() {
        redisLocationService.addCourierLocation(1L, 40.9909, 29.0303);

        List<Long> nearby = redisLocationService.findNearbyCouriers(40.9909, 29.0303);

        assertTrue(nearby.contains(1L));
    }

    @Test
    void findNearbyCouriers_whenMultipleCouriers_shouldReturnClosestFirst() {
        double centerLat = 40.9909;
        double centerLng = 29.0303;

        redisLocationService.addCourierLocation(1L, centerLat, centerLng);
        redisLocationService.addCourierLocation(2L, 41.0082, 28.9784);

        List<Long> nearby = redisLocationService.findNearbyCouriers(centerLat, centerLng);

        assertEquals(1L, nearby.get(0));
    }

    @Test
    void findNearbyCouriers_whenNoCourierInRadius_shouldReturnEmptyList() {
        redisLocationService.addCourierLocation(1L, 40.9909, 29.0303);

        List<Long> nearby = redisLocationService.findNearbyCouriers(39.9334, 32.8597);

        assertTrue(nearby.isEmpty());
    }
}
