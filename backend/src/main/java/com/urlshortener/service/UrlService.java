
package com.urlshortener.service;

import com.urlshortener.dto.ShortenRequest;
import com.urlshortener.dto.ShortenResponse;
import com.urlshortener.entity.UrlMapping;
import com.urlshortener.repository.UrlMappingRepository;
import com.urlshortener.util.SnowflakeIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class UrlService {
    private static final Logger log = LoggerFactory.getLogger(UrlService.class);
    private final UrlMappingRepository repository;
    private final SnowflakeIdGenerator idGenerator;
    private final RedisTemplate<String, String> redisTemplate;
    private final String baseUrl;
    private final int defaultTtlHours;

    public UrlService(UrlMappingRepository repository, SnowflakeIdGenerator idGenerator,
                      RedisTemplate<String, String> redisTemplate,
                      @Value("${app.base-url}") String baseUrl,
                      @Value("${app.short-code.ttl-hours}") int defaultTtlHours) {
        this.repository = repository;
        this.idGenerator = idGenerator;
        this.redisTemplate = redisTemplate;
        this.baseUrl = baseUrl;
        this.defaultTtlHours = defaultTtlHours;
    }

    @Transactional
    public ShortenResponse shorten(ShortenRequest req) {
        // Check if longUrl already exists (optional dedup)
        Optional<UrlMapping> existing = repository.findByLongUrl(req.getLongUrl());
        if (existing.isPresent() && req.getCustomAlias() == null) {
            UrlMapping e = existing.get();
            return new ShortenResponse(e.getShortCode(), baseUrl + "/" + e.getShortCode(), e.getLongUrl(), defaultTtlHours);
        }

        String shortCode;
        if (req.getCustomAlias() != null && !req.getCustomAlias().isBlank()) {
            if (repository.existsByShortCode(req.getCustomAlias())) {
                throw new IllegalArgumentException("Custom alias already taken: " + req.getCustomAlias());
            }
            shortCode = req.getCustomAlias();
        } else {
            // Snowflake + Base62 - collision-resistant, distributed
            shortCode = idGenerator.nextShortCode();
            int retries = 0;
            while (repository.existsByShortCode(shortCode) && retries < 3) {
                shortCode = idGenerator.nextShortCode();
                retries++;
            }
        }

        Instant expiresAt = Instant.now().plus(req.getTtlHours() != null ? req.getTtlHours() : defaultTtlHours, ChronoUnit.HOURS);
        UrlMapping mapping = new UrlMapping(shortCode, req.getLongUrl());
        mapping.setExpiresAt(expiresAt);
        repository.save(mapping);

        // Write-through cache
        try {
            redisTemplate.opsForValue().set(shortCode, req.getLongUrl(), defaultTtlHours, TimeUnit.HOURS);
            log.info("Cached {} -> {} TTL {}h", shortCode, req.getLongUrl(), defaultTtlHours);
        } catch (Exception ex) {
            log.warn("Redis cache set failed, fallback to DB: {}", ex.getMessage());
        }

        log.info("Shortened {} -> {} (TTL {}h)", req.getLongUrl(), shortCode, defaultTtlHours);
        return new ShortenResponse(shortCode, baseUrl + "/" + shortCode, req.getLongUrl(), defaultTtlHours);
    }

    public String resolve(String shortCode) {
        // 1. Cache lookup - 90% hit ratio, 2ms
        try {
            String cached = redisTemplate.opsForValue().get(shortCode);
            if (cached != null) {
                log.debug("Cache HIT for {}", shortCode);
                incrementClickAsync(shortCode);
                return cached;
            }
        } catch (Exception ex) {
            log.warn("Redis get failed, fallback to DB");
        }

        // 2. DB lookup on miss - 20ms + cache populate 5ms = 25ms
        log.debug("Cache MISS for {}, querying DB", shortCode);
        Optional<UrlMapping> mapping = repository.findByShortCode(shortCode);
        if (mapping.isEmpty()) throw new IllegalArgumentException("Short code not found: " + shortCode);

        UrlMapping m = mapping.get();
        if (m.getExpiresAt() != null && m.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Short URL expired: " + shortCode);
        }

        // Populate cache
        try {
            redisTemplate.opsForValue().set(shortCode, m.getLongUrl(), defaultTtlHours, TimeUnit.HOURS);
        } catch (Exception ignored) {}

        incrementClickAsync(shortCode);
        return m.getLongUrl();
    }

    private void incrementClickAsync(String shortCode) {
        // Async analytics - in prod use Kafka/Redis Stream
        new Thread(() -> {
            try {
                repository.findByShortCode(shortCode).ifPresent(m -> {
                    m.setClickCount(m.getClickCount() + 1);
                    repository.save(m);
                });
            } catch (Exception ignored) {}
        }).start();
    }
}
