# ADR 0002: Let the database enforce code uniqueness (race conditions on create)

- **Status:** Accepted
- **Date:** 2026-11-15

## Context
`POST /api/links` must never give two links the same code. The obvious approach is "check, then insert":

```
request A: SELECT ... WHERE code = 'bf26'  → not found
request B: SELECT ... WHERE code = 'bf26'  → not found
request A: INSERT 'bf26'                   → ok
request B: INSERT 'bf26'                   → duplicate! (or, without a constraint, two links with one code)
```

Between the check and the insert, another request can take the same code. This is a **race condition**
(a "check-then-act" bug). It happens with two threads in one app, and even more with several app instances.
A Java `synchronized` block cannot fix it, because it only protects one JVM.

## Decision
- The `UNIQUE` constraint on `links.code` is the **single source of truth**. We do not check first. We just insert.
- `create()` calls `saveAndFlush()`, so the INSERT runs right away and a duplicate fails *inside* our method
  as a `DataIntegrityViolationException` (not later, at commit time).
- **Generated code collides** → log a warning and try a new random code, up to **5 attempts**.
  After 5 failures → `CodeGenerationException` → **500** and an error log (at 2.2B possible codes this
  should never happen; if it does, something is wrong and we want to know).
- **Custom alias collides** → **409 Conflict** right away, no retry: the client asked for that exact code.
- `create()` is **not** `@Transactional`. Each `saveAndFlush()` runs in its own small transaction.
  If the whole method were one transaction, the first failed INSERT would mark it rollback-only,
  and the retry could not commit.

## Alternatives considered
- **Check with `existsByCode()` first, then insert:** has the race above. Rejected (we would still need the
  constraint, so the check only adds an extra query).
- **Lock (`SELECT ... FOR UPDATE` or a table lock):** you cannot lock a row that does not exist yet, and a
  table lock makes every create wait for the others. Rejected.
- **Pre-generate a pool of free codes:** no collisions, but extra moving parts (a table, a refill job).
  Overkill at our scale. Rejected.

## Consequences
- Correct under any number of threads or app instances, with no locks.
- A rare collision costs one extra INSERT.
- We depend on catching a Spring exception type; tests cover both paths (retry succeeds on the 2nd attempt,
  5 failures → 500, alias taken → 409).
