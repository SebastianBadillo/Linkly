# ADR 0001: How short codes are generated

- **Status:** Accepted
- **Date:** 2026-11-11

## Context
Every link needs a short code (`linkly.app/{code}`). Codes must be short, unique, and hard to guess,
so nobody can walk through `/1`, `/2`, `/3`… and read everyone's links. Users can also pick a custom alias
(e.g. `bf26`), and people often type aliases from posters in the wrong case.

## Decision
- **Random codes:** 6 characters from `a-z0-9` (base36), generated with `SecureRandom`.
  36⁶ ≈ 2.2 billion possible codes.
- **All codes are lowercase**, generated and custom alike. Incoming codes are lowercased before lookup,
  and the database enforces it with `CHECK (code = lower(code))`.
- **Generated codes and aliases share one `code` column** with a `UNIQUE` constraint.
  The database is the single source of truth for uniqueness:
  - a generated code that collides is silently retried (up to 5 times);
  - a custom alias that is already taken returns **409 Conflict** (first come, first served, like domain names).
- **Alias rules:** 3–30 characters, only `a-z 0-9 - _`, and not a reserved word (`api`, `actuator`, `login`, …).

## Alternatives considered
- **Base62 of the database ID** (`1 → "1"`, `125 → "21"`): never collides and needs no retry, but codes are
  sequential and **guessable** (enumeration attack). Rejected.
- **Case-sensitive base62** (57 billion codes): more codes, but `Hola` and `hOla` would be different links
  that look the same, which is confusing and a phishing risk. Rejected.
- **Aliases must be 7+ characters** (so they can never overlap with 6-char generated codes): avoids a rare retry,
  but blocks short aliases like `bf26`, and doesn't solve alias-vs-alias conflicts. The unique constraint
  already prevents overlap. Rejected.
- **`java.util.Random`:** faster, but its output can be predicted from previous values. Rejected.

## Consequences
- Fewer possible codes (2.2B instead of 57B). With 1M links, a new code collides about 1 time in 2,000,
  which the retry handles. If the table grows to hundreds of millions of links, increase the length to 7.
- Creating a link may need a second INSERT in rare cases.
- The redirect lookup uses the B-tree index created by the `UNIQUE` constraint.
