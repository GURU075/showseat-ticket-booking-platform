package com.guru.seat_inventory_service.config;

import org.springframework.data.redis.core.script.RedisScript;

public record SeatLockScripts(
        RedisScript<Long> acquire,
        RedisScript<Long> verifyAndExtend,
        RedisScript<Long> release
) {
}
