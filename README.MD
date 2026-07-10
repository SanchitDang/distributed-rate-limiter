# Distributed Rate Limiter

> **Goal**: A production‑grade, distributed rate limiting system that demonstrates deep understanding of **concurrency, distributed systems, and real‑world trade‑offs**.

---

## 1. Problem Definition (What We Are Solving)

We want to **control how many requests** a client can make to a system over time **across multiple servers**.

### Functional Requirements

* Limit requests by:
    * IP
    * User ID
    * Organization
* Support multiple algorithms:
    * Token Bucket
    * Sliding Window Log (optimized) 👉 *Future Scope*
    * Sliding Window Counter (optimized) 👉 *Future Scope*
* Distributed correctness (multiple app instances)
* Low latency (< 5ms overhead)

### Non‑Functional Requirements

* Highly concurrent
* Fault-tolerant
* Horizontally scalable
* Deterministic behavior under load

---

## 2. High‑Level Architecture

```
                ┌──────────────┐
                │   Client     │
                └──────┬───────┘
                       │
                ┌──────▼───────┐
                │ API Gateway  │
                └──────┬───────┘
                       │
        ┌──────────────▼──────────────┐
        │ Rate Limiter Service (Java) │
        │  - Algorithm Engine         │
        │  - Policy Resolver          │
        │  - Concurrency Control      │
        └──────────────┬──────────────┘
                       │
                ┌──────▼───────┐
                │    Redis     │
                │ (Atomic Ops) │
                └──────────────┘
```

**Key Idea**: All state lives in Redis → app servers are stateless.

---

## 3. Core Concepts & Data Modeling

### 3.1 Identity Keys

Each request maps to a **rate‑limit key**:

```
rate_limit:{scope}:{identifier}

Examples:
rate_limit:ip:192.168.1.1
rate_limit:user:12345
rate_limit:org:acme_corp
```

---

## 4. Algorithms (DSA‑Heavy Core)

### 4.1 Token Bucket (Primary Algorithm)

**Idea**: Tokens refill at a fixed rate. Each request consumes 1 token.

#### Data Stored in Redis

```
{
  tokens: float,
  last_refill_timestamp: long
}
```

#### Steps (Per Request)

1. Fetch current bucket
2. Refill tokens based on elapsed time
3. If tokens >= 1 → allow
4. Else → reject
5. Persist updated state atomically

#### DSA Used

* Hash Maps
* Floating‑point math
* Time‑based computation

---

### 4.2 Sliding Window Log (Accurate, Expensive) 👉 *Future Scope*

Store **timestamps of requests**.

```
Redis Sorted Set:
key = rate_limit:user:123
value = timestamp
```

Steps:

1. Remove timestamps < window_start
2. Count remaining entries
3. Allow / reject

DSA:

* Queue / Deque
* Sorted Set

---

### 4.3 Sliding Window Counter (Optimized) 👉 *Future Scope*

Split time into fixed buckets.

```
current_window_count * weight + previous_window_count * (1 - weight)
```

DSA:

* Hash maps
* Time buckets

---

## 5. Redis Atomicity (CRITICAL)

### Why Atomic Operations Matter

Without atomicity:

* Race conditions
* Over‑allowing requests

### Solution

Use **Lua scripts** in Redis:

* Read state
* Compute new state
* Write back

All in **one atomic operation**.

---

## 6. Java Service Internal Architecture

```
RateLimiterController
 ├── LocalHotKeyRateLimiter      (local pre-filter, shed-only, never allows on its own)
 ├── RedisHierarchicalRateLimiter (authoritative IP -> User -> Org check, wired live)
 │     ├── TokenBucketRateLimiter / RedisTokenBucketRateLimiter / RedisDynamicRateLimiter
 │     │     (earlier build steps, kept as standalone classes + tests, not Spring-wired)
 │     ├── SlidingWindowLimiter 👉 *Future Scope*
 │
 ├── DefaultPolicyResolver        (builds rate_limit:{scope}:{id} keys)
 ├── AdminConfigController        (writes per-key capacity/refill config)
 ├── InMemoryRateLimiterMetrics
```

---

## 7. Concurrency Handling (In‑Process)

### Problem

Multiple threads can hit the limiter simultaneously.

### Strategy

* Redis handles cross‑node consistency
* Java handles:

    * Thread pools
    * Time calculations

Use:

* `ExecutorService`
* `CompletableFuture`

---

## 8. Request Flow (Step‑by‑Step)

```
Client Request
   ↓
Extract identity (IP / User / Org)
   ↓
Resolve policy (limits, algorithm)
   ↓
Execute Redis Lua script
   ↓
Allowed ? Forward : Reject (429)
```

---

## 9. Hierarchical Rate Limiting (Advanced)

Request must pass **ALL levels**:

```
IP → User → Organization
```

Algorithm:

1. Check IP bucket
2. Check user bucket
3. Check org bucket

Fail fast on first violation - each level reads its own `capacity`/`refill_rate`
from Redis (`<key>:config`), so IP/user/org can each carry a different budget.
Internally the limiter knows which key blocked the request, which is counted
against `blockedIp`/`blockedUser`/`blockedOrg` in the metrics (the HTTP
response itself is still just an allow/reject, not the blocked level).

---

## 10. Hot Key Mitigation

### Problem

One key gets massive traffic.

### Solution

A local, per-JVM pre-filter (`LocalHotKeyRateLimiter`) sheds load on a hot key
before it reaches Redis - it only ever short-circuits a **reject**, never an
allow, so Redis is still the authoritative check for anything it lets through.
Each request picks a random shard out of `shardCount` local buckets, spreading
a single hot key's traffic instead of pinning it to one lock.

---

## 11. Dynamic Configuration

### Requirement

Change limits without redeploying.

### Design

* Config stored in Redis, per key: `rate_limit:{scope}:{id}:config` → `capacity`, `refill_rate`
* Read on every request directly from Redis (no local caching/refresh - always current)
* Written via `POST /admin/config?scope=user&id=123&capacity=50&refillRate=10`
  (`scope` is one of `ip`/`user`/`org`; no auth on this endpoint - it's meant
  for local/demo use, not a real admin surface)

---

## 12. Failure Scenarios & Trade‑offs

### Redis Down

Options:

* Fail open (allow traffic)
* Fail closed (block traffic)

---

## 13. Metrics & Observability

Track:

* Allowed vs blocked requests
* Which level blocked a hierarchical request (`blockedIp` / `blockedUser` / `blockedOrg`)
* Local hot-key hits vs Redis hits
* Redis latency and failure count
* Key cardinality

Expose `/metrics` (JSON) and `/prometheus/metrics` (Prometheus text format) endpoints.

---

## 14. Testing Strategy

Run with `mvn test`. Most tests need a local Redis on `localhost:6379` (no
Testcontainers/mocking - they're integration tests, and reset their own Redis
keys at the start so they don't depend on the 60s bucket TTL).

### Unit Tests

* Token refill logic, including edge cases (zero refill rate, capacity clamping)
* Hot-key sharding (random shard distribution across a hot key's traffic)
* Individual + combined policy tests (user, IP, org)

### Concurrency Tests

* Multi‑threaded request simulation
* Hierarchical policy tests (combined user + IP + org), including per-level
  capacity and blocked-level reporting

### Load Tests

* Burst traffic
* `RateLimiterLoadTest`/`DistributedTest` are manual scripts (`main` methods,
  not `@Test`s) for simulating HTTP load or multi-process runs by hand - see
  the comment at the top of each for how to run them

---

## 15. Build Roadmap

### Phase 1 – Core Engine

* Implement Token Bucket in Java
* Unit test thoroughly

### Phase 2 – Redis Integration

* Lua scripts
* Atomic guarantees

### Phase 3 – API Layer

* REST endpoint
* Request interceptors

### Phase 4 – Distributed Concerns

* Multi‑instance testing

### Phase 5 – Advanced Features

* Hierarchy
* Dynamic config
* Metrics

---

# Enhancements

## 16. Prometheus & Grafana Integration

Exposes Prometheus metrics for allowed/rejected requests, hot keys, and Redis performance.
Prometheus is provisioned to scrape `/prometheus/metrics` out of the box; Grafana
itself ships with no pre-built datasource or dashboards, so you'll need to point
it at the Prometheus container and build panels yourself.
**Access Grafana:** `http://localhost:3000` (default username/password:
`admin`/`admin`, overridable via the `GRAFANA_ADMIN_PASSWORD` env var - see below)

---

## 17. Docker Setup (One-Click)

Docker Compose setup to run the rate limiter, Redis, Prometheus, and Grafana together.
Quick local setup and consistent environments across machines.

Redis requires a password (`--requirepass`), read from the `REDIS_PASSWORD` env
var and defaulting to `changeme` if unset - override it (and `GRAFANA_ADMIN_PASSWORD`)
in your shell or a `.env` file before running compose. Prometheus and Grafana
data now persist in named volumes across `docker-compose down`/`up`.

### Run & Build

```bash
docker-compose up --build
```

---
