-- API keys identify WHO calls /api/** (and give the rate limiter one bucket per key).
-- We never store the raw key, only its SHA-256 hash (hex). A DB leak then reveals no usable keys.
CREATE TABLE api_keys (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- 64 hex chars = 256-bit SHA-256. UNIQUE also creates the index used on every request (WHERE key_hash = ?).
    key_hash    VARCHAR(64)  NOT NULL,
    CONSTRAINT uk_api_keys_key_hash UNIQUE (key_hash),

    name        VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- NULL = active. We revoke instead of deleting, so we keep the history.
    revoked_at  TIMESTAMPTZ
);
