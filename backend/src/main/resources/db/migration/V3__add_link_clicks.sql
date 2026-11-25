-- Total clicks per link. The app adds batches to it every few seconds (write-behind),
-- so it can be slightly behind the real number (eventual consistency).
-- BIGINT: an INTEGER overflows at about 2.1 billion.
ALTER TABLE links
    ADD COLUMN clicks BIGINT NOT NULL DEFAULT 0;
