-- A short link: a short code that redirects to a target URL.
-- The owner column is added later (V2, with API keys in M2).
CREATE TABLE links (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- Generated codes and custom aliases share this column.
    -- UNIQUE also creates the B-tree index that redirects use (WHERE code = ?).
    -- All codes are lowercase, so "BF26" and "bf26" can never be two different links.
    code        VARCHAR(30) NOT NULL,
    CONSTRAINT uk_links_code UNIQUE (code),
    CONSTRAINT ck_links_code_lowercase CHECK (code = lower(code)),

    target_url  TEXT        NOT NULL,
    CONSTRAINT ck_links_target_url_length CHECK (length(target_url) <= 2048),

    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
