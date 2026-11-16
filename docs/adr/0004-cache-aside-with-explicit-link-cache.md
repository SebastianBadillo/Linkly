# ADR 0004: Cache-aside with an explicit `LinkCache` instead of `@Cacheable`

- **Status:** Accepted
- **Date:** 2026-11-16

## Context
Every redirect (`GET /{code}`) reads Postgres. Redirects are the hottest path and the data almost never
changes, so most of those reads are repeated work. We want to serve them from Valkey and **measure** the gain
later (k6, cache ON vs OFF, hit ratio).

Requirements:
- Valkey is a speed-up, **not** the source of truth. If Valkey is down or slow, redirects must still work.
- After a link is updated or deleted, the cache must not keep serving the old target for long.
- Unknown codes (typos, bots scanning random codes) should not hit Postgres every time.

## Decision
**Read path (cache-aside)**, key `link:{code}`:
- **Hit:** Valkey has the target URL → return 302 without touching Postgres.
- **Miss:** read Postgres, store the URL in Valkey with a TTL, return 302.
- **Not found:** store a `__NOT_FOUND__` marker (negative caching) and return 404, so repeated bad codes
  don't reach Postgres.

**Write path:** on update/delete, write Postgres **first, then evict** the key. If we evicted first, a reader
could miss, read the old value from Postgres before our write commits, and put it back in the cache.

**TTLs (safety net):** `linkly.cache.ttl=1h` for URLs, `linkly.cache.not-found-ttl=60s` for the marker.
We evict on every write; the TTL only cleans up when an evict was missed. The not-found TTL is short so a
link created after a miss becomes reachable quickly.

**Failures:** `LinkCache` catches every Valkey error (GET, SET, DEL), logs a warning and carries on.
A failed GET counts as a miss (we use Postgres), a failed SET just means no caching, and a failed DEL means
the stale value lives until its TTL.

**Why a hand-written `LinkCache` and not `@Cacheable`:** `@Cacheable` could also store data in Valkey,
so this is not about where the cache lives. We wanted (1) fail-soft error handling with no custom
`CacheErrorHandler`, (2) a different TTL for not-found markers, and (3) logic that is plain code,
easy to unit test and explain.

## Alternatives considered
- **`@Cacheable` / `@CacheEvict` (Spring cache abstraction):** less code, but it hides the logic. Error
  handling needs a custom `CacheErrorHandler`, negative caching with its own shorter TTL is awkward, and in an
  interview "the annotation does it" is weaker than code we can step through. Rejected for now.
- **Write-through (update the cache on every write):** keeps the cache warm, but two writers can race and
  leave the older value in the cache. Evicting is simpler and safer: the next read reloads the truth.
- **Evict before the Postgres write:** a reader in between can re-cache the old value from Postgres
  (the write hasn't committed yet). Rejected.
- **No negative caching:** simpler, but every request for a bad code reaches Postgres.

## Consequences
- Repeated redirects for the same code do not touch Postgres (cache hit).
- Short windows of inconsistency are accepted: if an evict fails, the stale value lives until its TTL
  (up to 1 h for a URL, up to 60 s for a not-found marker, e.g. a just-created link returning 404).
  **The TTL bounds how long the cache can be wrong.**
- Valkey errors are logged and swallowed, so a Valkey outage means slower redirects, not failed ones.
- Not handled yet: cache stampede on a hot key that expires (stretch goal), and the hit ratio metric (M3).
