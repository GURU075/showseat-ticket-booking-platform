package com.guru.seat_inventory_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Clock;
//1. Provide the current UTC time through Clock
//2. Load the three Redis Lua scripts
@Configuration
public class SeatLockRedisConfig {

    @Bean
    Clock applicationClock() {
        return Clock.systemUTC();
    }

    @Bean
    SeatLockScripts seatLockScripts() {
        return new SeatLockScripts(
                script("redis/acquire-seat-locks.lua"),
                script("redis/verify-and-extend-seat-locks.lua"),
                script("redis/release-seat-locks.lua")
        );
    }

    private RedisScript<Long> script(String path) {
        return RedisScript.of(
                new ClassPathResource(path),
                Long.class
        );
    }
}
