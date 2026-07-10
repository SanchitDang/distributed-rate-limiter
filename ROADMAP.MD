# **Distributed Rate Limiter — Roadmap**

---

## **Step 1: Core Token Bucket (Single-threaded, Java)**

**Goal:**
Implement the **basic token bucket algorithm** in-memory to understand refill logic and rate-limiting behavior.

**What we did:**

* Built `TokenBucket` class
* Tokens are refilled over time
* Requests are allowed/rejected based on available tokens

**Result:**
Algorithm works for single-threaded requests
Correct token calculation, allows bursts

**DSA / Concepts Used:**

* Sliding window logic (time-based refill)
* O(1) per request
* HashMap for mapping keys (later per-user buckets)

---

## **Step 2: Thread-Safe Token Bucket (Multi-threaded, Java)**

**Goal:**
Make the token bucket **safe for concurrent access in a single JVM**.

**What we did:**

* Added `ReentrantLock` per bucket
* Used `ConcurrentHashMap` for key → bucket mapping
* Ensured atomic refill + consume operations

**Result:**
Thread-safe single-node rate limiter
No race conditions under high concurrency

**DSA / Concepts Used:**

* Locks (`ReentrantLock`) for critical section
* ConcurrentHashMap for thread-safe bucket storage
* Atomic operations

---

## **Step 3: Redis Data Model & Distributed State Design**

**Goal:**
Enable **multiple app instances** to share state. Move bucket state to Redis.

**What we planned:**

* Redis keys: `rate_limit:{scope}:{id}`
* Redis values: `tokens` + `last_refill` (hash)
* Atomic refill + consume using **Lua scripts**
* TTL to auto-expire inactive buckets

**Result:**
Single source of truth across multiple servers
Scalable, atomic, distributed-ready design

**Concepts Used:**

* Redis hash data structure
* Lua scripts for atomic operations
* CAP trade-offs discussion (strong consistency for rate-limiting)
* Distributed system design

---

## **Step 4: Redis Lua Atomic Token Bucket**

**Goal:**
Implement **actual Redis-backed token bucket** using Lua and integrate with Java.

**What we did / need to correct:**

* Lua script performs: fetch → refill → check → decrement → update
* Java `RedisTokenBucketRateLimiter` executes Lua script per request
* Correct refill calculation (tokens/sec → tokens/ms)
* Handle first-time bucket creation

**Expected Result:**
Distributed, atomic token bucket
Correctly limits requests across multiple nodes
Safe even with concurrent requests

**DSA / Concepts Used:**

* Lua scripting (atomic operation)
* Redis TTL for memory management
* Java-Jedis integration
* Distributed consistency

---

## **Step 5: Hierarchical Rate Limiting (IP → User → Org)**

**Goal:**
Support **multi-level limits** per request. Example:

* IP limit (prevent abuse)
* User limit (individual rate)
* Org limit (enterprise-wide rate)

**Plan:**

* Run Lua script on multiple keys per request
* Fail fast if any level is violated
* Return **which level blocked the request**

**Expected Result:**
More robust and realistic rate limiter
Fine-grained control per level

**DSA / Concepts Used:**

* Multiple key evaluation in Redis Lua
* Fail-fast logic
* Hierarchical key design

---

## **Step 6: Dynamic Configuration**

**Goal:**
Allow rate limits (capacity, refill rate) to be **updated without redeploying**.

**Plan:**

* Store configuration in Redis or external config service
* Lua script reads **dynamic values** per request
* Update TTL / refill rates dynamically

**Expected Result:**
Admins can change limits on the fly
No downtime required
Supports feature flags

**Concepts Used:**

* Config as code / dynamic properties
* Redis as config storage
* Atomic updates

---

## **Step 7: Hot Key Mitigation**

**Goal:**
Prevent **single key (hot user/IP) from overwhelming Redis**.

**Plan:**

* Local pre-check cache for high-frequency keys
* Batch increments for burst traffic
* Optional Redis sharding / consistent hashing

**Expected Result:**
Stable performance under extreme load
Redis nodes not blocked by one key

**Concepts Used:**

* Caching / pre-check
* Sharding
* Backpressure techniques

---

## **Step 8: Testing & Validation**

**Goal:**
Ensure system works **under concurrent load and distributed deployment**.

**Plan:**

* Multithreaded local tests (already partially done)
* Multi-node simulation (multiple JVMs hitting same Redis)
* Measure allowed/rejected requests vs theoretical limit

**Expected Result:**
Verified correctness for concurrency and distribution
Metrics/logging show rate limits respected

**Concepts Used:**

* JUnit / integration tests
* Concurrency testing
* Distributed systems validation

---

## **Step 9: Metrics & Observability (Optional)**

**Goal:**
Track **rate-limiting events** for monitoring and debugging.

**Plan:**

* Count allowed vs rejected requests
* Store metrics in Prometheus / Grafana
* Track hot keys

**Expected Result:**
Observable system
Can detect abuse / scaling issues

**Concepts Used:**

* Monitoring & observability
* Metrics collection
* Alerting for abnormal usage

---

## **Step 10: Bug Fixes & Hardening Pass**

**Goal:**
A few of the earlier steps didn't quite work as described once looked at closely
- fix those gaps so the implementation actually matches this roadmap.

**What we found & fixed:**

* The hot-key local limiter (Step 7) was short-circuiting straight past the
  Redis check on every allow, not just shedding load - fixed so it can only
  ever short-circuit a reject, Redis stays authoritative
* Its "sharding" always hashed a key to the same shard, so it never actually
  spread load - shard selection is now random per request
* The hierarchical limiter (Step 5) used one shared capacity/refill for every
  level and was never actually wired into the running app - it now reads
  per-level config from Redis (same as Step 6's dynamic config) and is the
  live path; it also reports which level blocked a request, wired to metrics
* Lua's `now` came from each app node's own clock, which meant clock drift
  between instances could corrupt refill math - it now reads Redis's own
  `TIME` instead, so there's a single source of truth regardless of which
  node made the call
* Redis failures and missing per-key config were silently swallowed - both
  are now logged
* Dynamic config (Step 6) had no write path - added `POST /admin/config`
* `pom.xml` was missing the JUnit 5 engine/launcher, so `mvn test` likely
  wasn't running anything - fixed, plus added tests for the gaps above
* Light Docker hardening: Redis now requires a password, Prometheus/Grafana
  persist across restarts, Grafana admin password is overridable
* Removed a dead duplicate policy-resolver implementation that was never used

**Result:**
Hierarchical limiting, hot-key mitigation, and dynamic config now work the
way the earlier steps describe, instead of just partially.

---

### Summary

| Step | Focus                        | Outcome                         | Key Tech / Concept                    |
|------|------------------------------|---------------------------------|---------------------------------------|
| 1    | Single-threaded token bucket | Basic algorithm works           | HashMap, sliding window               |
| 2    | Thread-safe                  | Concurrency safe in JVM         | ReentrantLock, ConcurrentHashMap      |
| 3    | Redis model                  | Distributed state design        | Redis hash, TTL, Lua design           |
| 4    | Redis Lua implementation     | Atomic distributed token bucket | Lua scripts, Jedis                    |
| 5    | Hierarchical limits          | Multi-level rate limiting       | Lua multi-key, fail-fast              |
| 6    | Dynamic config               | Update limits without redeploy  | Redis config, dynamic properties      |
| 7    | Hot key mitigation           | Prevent Redis overload          | Caching, sharding                     |
| 8    | Testing & validation         | Ensure correctness              | Multi-thread tests, distributed tests |
| 9    | Metrics & observability      | Monitor system                  | Prometheus, Grafana                   |
| 10   | Bug fixes & hardening        | Matches what steps 5-9 describe | Redis clock, real sharding, junit fix |

---

