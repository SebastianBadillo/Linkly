-- Token bucket, run atomically inside Valkey (no other command can run in the middle of a script).
-- KEYS[1] = ratelimit:{keyId}   ARGV[1] = capacity   ARGV[2] = seconds per token
-- Returns { allowed (1 or 0), tokens left as a string }.
local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refillSeconds = tonumber(ARGV[2])

-- One clock for every app instance: the Valkey clock. TIME returns { seconds, microseconds }.
local t = redis.call('TIME')
local now = tonumber(t[1]) + tonumber(t[2]) / 1000000

local bucket = redis.call('HMGET', key, 'tokens', 'lastRefill')
local tokens = tonumber(bucket[1])
local lastRefill = tonumber(bucket[2])
if tokens == nil or lastRefill == nil then
  -- A missing bucket is a full bucket.
  tokens = capacity
  lastRefill = now
end

-- Add, then cap, then check.
local elapsed = math.max(0, now - lastRefill)
tokens = math.min(capacity, tokens + elapsed / refillSeconds)

local allowed = 0
if tokens >= 1 then
  tokens = tokens - 1
  allowed = 1
end

redis.call('HSET', key, 'tokens', tostring(tokens), 'lastRefill', tostring(now))
-- After this long the bucket is full again anyway, so Valkey may delete it (= full bucket).
redis.call('EXPIRE', key, math.ceil(capacity * refillSeconds))

-- tostring: Lua numbers sent back to the client are cut to integers (0.67 would become 0).
return { allowed, tostring(tokens) }
