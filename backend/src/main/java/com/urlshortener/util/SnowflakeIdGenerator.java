
package com.urlshortener.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class SnowflakeIdGenerator {
    private static final long CUSTOM_EPOCH = 1704067200000L; // Jan 1 2024
    private static final long SHARD_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long MAX_SHARD_ID = (1L << SHARD_ID_BITS) - 1;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;
    private static final long TIMESTAMP_SHIFT = SHARD_ID_BITS + SEQUENCE_BITS;
    private static final long SHARD_SHIFT = SEQUENCE_BITS;

    private final long shardId;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator(@Value("${app.snowflake.shard-id:1}") long shardId) {
        if (shardId < 0 || shardId > MAX_SHARD_ID) throw new IllegalArgumentException("shardId out of range");
        this.shardId = shardId;
    }

    public synchronized long nextId() {
        long currentTimestamp = System.currentTimeMillis();
        if (currentTimestamp < lastTimestamp) throw new RuntimeException("Clock moved backwards");

        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                currentTimestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0;
        }
        lastTimestamp = currentTimestamp;
        return ((currentTimestamp - CUSTOM_EPOCH) << TIMESTAMP_SHIFT) |
               (shardId << SHARD_SHIFT) |
               sequence;
    }

    private long waitNextMillis(long lastTs) {
        long ts = System.currentTimeMillis();
        while (ts <= lastTs) ts = System.currentTimeMillis();
        return ts;
    }

    public String nextShortCode() {
        long id = nextId();
        String encoded = Base62Encoder.encode(id);
        // Pad/truncate to 7 chars - take last 7
        if (encoded.length() > 7) return encoded.substring(encoded.length() - 7);
        if (encoded.length() < 7) return String.format("%7s", encoded).replace(' ', '0');
        return encoded;
    }
}
