package com.guru.seat_inventory_service.lock;

import com.guru.seat_inventory_service.config.SeatLockProperties;
import com.guru.seat_inventory_service.config.SeatLockScripts;
import com.guru.seat_inventory_service.exception.ExternalServiceException;
import com.guru.seat_inventory_service.exception.ResourceNotFoundException;
import com.guru.seat_inventory_service.exception.SeatConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class RedisSeatLockStore implements SeatLockStore {

    private static final String LOCK_PREFIX = "LOCK-";

    private final StringRedisTemplate redisTemplate;
    private final SeatLockScripts scripts;
    private final SeatLockProperties properties;
    private final Clock clock;

    @Override
    public SeatLockDetails acquire(Long showId, Long userId, List<String> seatNumbers) {
        String lockId = LOCK_PREFIX + UUID.randomUUID();
        Instant expiresAt = clock.instant().plus(properties.ttl());
        SeatLockDetails details = new SeatLockDetails(lockId, showId, userId, seatNumbers, expiresAt);
        List<String> keys = keys(details);

        Long result = redis(() -> redisTemplate.execute(
                scripts.acquire(),
                keys,
                seatValue(lockId, userId),
                encodeGroup(details),
                milliseconds(properties.ttl())
        ));
        requireScriptResult(result);
        if (result == 0L) {
            throw new SeatConflictException("One or more selected seats are already locked");
        }
        return details;
    }

    @Override
    public Set<String> findLockedSeatNumbers(Long showId, Collection<String> seatNumbers) {
        if (seatNumbers.isEmpty()) {
            return Set.of();
        }

        List<String> orderedSeats = List.copyOf(seatNumbers);
        List<String> values = redis(() -> redisTemplate.opsForValue().multiGet(
                orderedSeats.stream().map(seat -> seatKey(showId, seat)).toList()
        ));
        if (values == null || values.size() != orderedSeats.size()) {
            throw new ExternalServiceException("Redis returned an invalid multi-get response");
        }

        Set<String> locked = new HashSet<>();
        for (int index = 0; index < orderedSeats.size(); index++) {
            if (values.get(index) != null) {
                locked.add(orderedSeats.get(index));
            }
        }
        return Set.copyOf(locked);
    }

    @Override
    public SeatLockDetails verifyAndExtend(Long showId, Long userId, String lockId) {
        OwnedGroup group = readOwnedGroup(showId, userId, lockId);
        Long result = redis(() -> redisTemplate.execute(
                scripts.verifyAndExtend(),
                keys(group.details()),
                seatValue(lockId, userId),
                group.encodedValue(),
                milliseconds(properties.confirmationTtl())
        ));
        requireActiveLock(result, showId, lockId);
        return new SeatLockDetails(
                lockId,
                showId,
                userId,
                group.details().seatNumbers(),
                clock.instant().plus(properties.confirmationTtl())
        );
    }

    @Override
    public SeatLockDetails release(Long showId, Long userId, String lockId) {
        OwnedGroup group = readOwnedGroup(showId, userId, lockId);
        Long result = redis(() -> redisTemplate.execute(
                scripts.release(),
                keys(group.details()),
                seatValue(lockId, userId),
                group.encodedValue()
        ));
        requireActiveLock(result, showId, lockId);
        return group.details();
    }

    private OwnedGroup readOwnedGroup(Long showId, Long userId, String lockId) {
        String encoded = redis(() -> redisTemplate.opsForValue().get(groupKey(showId, lockId)));
        if (encoded == null) {
            throw activeLockNotFound(showId, lockId);
        }

        SeatLockDetails details = decodeGroup(encoded);
        if (!showId.equals(details.showId()) || !lockId.equals(details.lockId())) {
            throw new ExternalServiceException("Redis returned inconsistent seat lock metadata");
        }
        if (!userId.equals(details.userId())) {
            throw new SeatConflictException("Seat lock belongs to a different user");
        }
        return new OwnedGroup(details, encoded);
    }

    private String encodeGroup(SeatLockDetails details) {
        return String.join(
                "|",
                details.lockId(),
                details.showId().toString(),
                details.userId().toString(),
                Long.toString(details.expiresAt().toEpochMilli()),
                String.join(",", details.seatNumbers())
        );
    }

    private SeatLockDetails decodeGroup(String encoded) {
        try {
            String[] fields = encoded.split("\\|", 5);
            if (fields.length != 5 || fields[4].isBlank()) {
                throw new IllegalArgumentException("missing fields");
            }
            List<String> seatNumbers = Arrays.stream(fields[4].split(","))
                    .filter(value -> !value.isBlank())
                    .toList();
            if (seatNumbers.isEmpty()) {
                throw new IllegalArgumentException("missing seats");
            }
            return new SeatLockDetails(
                    fields[0],
                    Long.valueOf(fields[1]),
                    Long.valueOf(fields[2]),
                    seatNumbers,
                    Instant.ofEpochMilli(Long.parseLong(fields[3]))
            );
        } catch (RuntimeException ex) {
            throw new ExternalServiceException("Redis returned malformed seat lock metadata", ex);
        }
    }

    private List<String> keys(SeatLockDetails details) {
        List<String> keys = new ArrayList<>(details.seatNumbers().size() + 1);
        details.seatNumbers().forEach(seat -> keys.add(seatKey(details.showId(), seat)));
        keys.add(groupKey(details.showId(), details.lockId()));
        return keys;
    }

    private String seatKey(Long showId, String seatNumber) {
        return "seat-lock:{show:" + showId + "}:seat:" + seatNumber;
    }

    private String groupKey(Long showId, String lockId) {
        return "seat-lock:{show:" + showId + "}:group:" + lockId;
    }

    private String seatValue(String lockId, Long userId) {
        return lockId + "|" + userId;
    }

    private String milliseconds(Duration duration) {
        return Long.toString(duration.toMillis());
    }

    private void requireScriptResult(Long result) {
        if (result == null) {
            throw new ExternalServiceException("Redis lock script returned no result");
        }
    }

    private void requireActiveLock(Long result, Long showId, String lockId) {
        requireScriptResult(result);
        if (result == 0L) {
            throw activeLockNotFound(showId, lockId);
        }
    }

    private ResourceNotFoundException activeLockNotFound(Long showId, String lockId) {
        return new ResourceNotFoundException(
                "Active seat lock not found for show " + showId + " and lock " + lockId
        );
    }

    private <T> T redis(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (DataAccessException ex) {
            throw new ExternalServiceException("Redis seat lock store is unavailable", ex);
        }
    }

    private record OwnedGroup(SeatLockDetails details, String encodedValue) {
    }
}
