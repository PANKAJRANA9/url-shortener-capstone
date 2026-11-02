# URL Shortener (Bitly Clone) - Capstone Project 2 v2.0
## FSD + Architect Level

### What it seems like
Simple app: long URL -> short code

### What it actually teaches (Interview Depth)
- **Database Indexing**: short_code UNIQUE B-Tree INDEX (O(log n) lookup), long_url INDEX for dedup, covering index for analytics, 100% index hit on hot path
- **Caching Strategies**: Cache-Aside (lazy) + Write-Through, Redis Cluster LRU, 90% hit ratio, 2ms hit vs 25ms miss (DB 20ms + SET 5ms), Bloom Filter negative cache to avoid DB for invalid codes
- **Unique ID Generation at Scale**: Snowflake 64-bit (41b timestamp + 10b shard + 12b sequence = 4M IDs/ms globally), thread-safe, k-sortable, Base62 -> 7 chars (62^7 = 3.5T combos), collision-resistant, no DB call
- **High-Read Traffic**: 100:1 read/write, CDN edge cache 300+ PoPs, Redis hot keys, PostgreSQL Primary + 2 Read Replicas, sharding hash(short_code)%4, async analytics via Kafka/Redis Stream

### Quick Start
```bash
cd url-shortener-capstone
docker compose build --no-cache
docker compose up -d --force-recreate

# Test
curl -X POST http://localhost:8080/api/v1/shorten -H "Content-Type: application/json" -d '{"longUrl":"https://example.com/very/long/url"}'
# Returns {"shortCode":"k3J9pQz","shortUrl":"http://localhost:8080/k3J9pQz"}

curl -i http://localhost:8080/k3J9pQz
# 302 Redirect to long URL, async click tracked

# Frontend
open http://localhost:5173
```

### API
- POST /api/v1/shorten {longUrl, customAlias?, ttlHours?} -> 201 {shortCode, shortUrl, longUrl, expiresInHours}
- GET /{shortCode} -> 302 Redirect + analytics async
- GET /api/v1/stats/{shortCode} -> clicks, etc
- GET /health -> UP

### Architecture
See docs/HLD_LLD.md + images in /mnt/data/resource/

### Load Test
```bash
# k6 10k RPS
k6 run load-test/k6-script.js
```

### DB Schema
```sql
CREATE TABLE url_mappings (
  id UUID PK,
  short_code VARCHAR(10) UNIQUE INDEX,
  long_url VARCHAR(2048) INDEX,
  created_at TIMESTAMP,
  expires_at TIMESTAMP,
  click_count BIGINT,
  created_by VARCHAR(100)
);
-- 7 chars Base62 = 3.5T combos, 1M URLs = 0.00003% collision
```
