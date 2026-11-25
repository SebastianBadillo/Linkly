# Spec: click counting (write-behind)

## Redirect (`GET /{code}`)
- After a successful lookup (not on 404): `INCR click:{code}`, then `SADD clicks:pending {code}`.
- If Valkey fails, the redirect still returns 302. The click is lost and the metric
  `linkly.clicks.failed` goes up by 1.

## Storage
- Flyway V3 adds `links.clicks BIGINT NOT NULL DEFAULT 0`.
- Postgres is the source of truth. The count is approximate and eventually consistent.
- The link JSON has a `clicks` field.

## Flush (scheduled, every 10 s, `CLICKS_FLUSH_INTERVAL`)
- `SPOP clicks:pending` takes up to 1000 codes.
- For each code: `GETDEL click:{code}`. Nil means skip.
- `UPDATE links SET clicks = clicks + n WHERE code = ?`. 0 rows (deleted link) is ignored.
- If the update fails: `INCRBY` the number back, `SADD` the code, continue with the other codes.
- If the restore also fails, or the app crashes mid-flush, those clicks are lost (accepted).
- Safe with several instances because `SPOP` and `GETDEL` are atomic.

## Other rules
- `INCR` before `SADD`, so a listed code already has its counter.
- Deleting a link discards its pending clicks.
- Editing a link never writes the `clicks` column.
