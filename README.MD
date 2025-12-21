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
    * Sliding Window Counter (optimized) (optimized) 👉 *Future Scope*
* Distributed correctness (multiple app instances) (multiple app instances)
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
RateLimiterService
 ├── RateLimiterEngine
 │     ├── TokenBucketLimiter
 │     ├── RedisBucketLimiter
 │     ├── SlidingWindowLimiter 👉 *Future Scope*
 │
 ├── PolicyResolver
 │     ├── UserPolicy
 │     ├── OrgPolicy
 │     ├── IpPolicy
 │
 ├── MetricsCollector
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

1. Check org bucket
2. Check user bucket
3. Check IP bucket

Fail fast on first violation.

---

## 10. Hot Key Mitigation

### Problem

One key gets massive traffic.

### Solutions

* Local in‑memory pre‑check
* Key sharding
* Request coalescing

---

## 11. Dynamic Configuration

### Requirement

Change limits without redeploying.

### Design

* Config stored in DB / Redis
* Cached locally
* Periodic refresh

---

## 12. Failure Scenarios & Trade‑offs

### Redis Down

Options:

* Fail open (allow traffic) 👉 *Configurable – Future Enhancement*
* Fail closed (block traffic)

---

## 13. Metrics & Observability

Track:

* Allowed vs blocked requests
* Redis latency
* Key cardinality

Expose `/metrics` endpoint.

---

## 14. Testing Strategy

### Unit Tests

* Token refill logic
* Individual policy tests (user, IP, org)

### Concurrency Tests

* Multi‑threaded request simulation
* Hierarchical policy tests (combined user + IP + org

### Load Tests

* Burst traffic
* API-level integration tests (simulating HTTP requests to /api/request)

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
