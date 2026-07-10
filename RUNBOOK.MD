# Running This Project (A-Z)

Two ways to run it: the full Docker stack (Redis + API + Prometheus + Grafana),
or just the API against a local Redis for quicker dev/test loops.

---

## Option A: Full stack via Docker Compose

**Prerequisite:** Docker Desktop running.

### 1. Build and start everything

```bash
docker-compose up --build
```

Starts 4 containers: `redis`, `ratelimiter-api`, `prometheus`, `grafana`.
Redis requires a password (`--requirepass`), read from `REDIS_PASSWORD`
(defaults to `changeme` if unset). Override it before running compose:

```bash
export REDIS_PASSWORD=your-password
export GRAFANA_ADMIN_PASSWORD=your-grafana-password
docker-compose up --build
```

### 2. Confirm containers are healthy

```bash
docker compose ps
```

All 4 should show `Up`.

### 3. Hit the rate limiter API

```bash
curl "http://localhost:8080/api/request?user=alice"
curl "http://localhost:8080/api/request?ip=1.2.3.4"
curl "http://localhost:8080/api/request?user=alice&ip=1.2.3.4&org=acme"
```

`user`, `ip`, `org` are all optional but at least one is required. Repeat a
request enough times and you'll see `429` / `Rate limit exceeded`.

### 4. Change limits without redeploying

```bash
curl -X POST "http://localhost:8080/admin/config?scope=user&id=alice&capacity=50&refillRate=10"
```

`scope` is one of `ip` / `user` / `org`. No auth on this endpoint - local/demo only.

### 5. Check metrics

```bash
curl "http://localhost:8080/metrics"              # JSON
curl "http://localhost:8080/prometheus/metrics"   # Prometheus text format
```

### 6. Prometheus UI

Open `http://localhost:9090` → **Status → Targets**. The `rate-limiter` job
should show as `UP`, scraping `/prometheus/metrics` automatically
(pre-provisioned via `prometheus.yml`).

### 7. Grafana UI

Open `http://localhost:3000`, log in with `admin` / `admin` (or whatever you
set `GRAFANA_ADMIN_PASSWORD` to). Grafana ships with **no pre-built dashboard**
- add Prometheus as a datasource yourself:

* Connections → Data sources → Add data source → Prometheus
* URL: `http://prometheus:9090`
* Save & test, then build a panel/dashboard against the `ratelimiter_*` metrics.

### 8. Verify Redis actually requires the password

```bash
docker exec redis redis-cli ping                      # -> NOAUTH Authentication required.
docker exec redis redis-cli -a changeme ping           # -> PONG (or your overridden password)
```

### 9. Tear down

```bash
docker-compose down -v
```

The `-v` also removes the Prometheus/Grafana data volumes.

---

## Option B: Run the API locally (no Docker for the app itself)

**Prerequisite:** Java 17, and a local Redis on `localhost:6379` (either a
native install, or just `docker run -d -p 6379:6379 redis:7` - no password
needed for local dev).

### 1. Start Redis

```bash
docker run -d --name local-redis -p 6379:6379 redis:7
```

### 2. Run the app

From your IDE: run `RateLimiterApiApplication` directly (it defaults to the
`local` profile, which points at `localhost:6379` with no password).

Or from the command line:

```bash
mvn spring-boot:run
```

### 3. Use it the same way as Option A, steps 3-5

(Prometheus/Grafana aren't running in this mode - just the API + Redis.)

---

## Running the test suite

Needs a local Redis on `localhost:6379` (no password) - tests are
integration tests, not mocked.

```bash
docker run -d --name local-redis -p 6379:6379 redis:7   # if not already running
mvn test
```

Expect all tests to pass. `RateLimiterLoadTest` and `DistributedTest` are
**manual scripts**, not part of `mvn test` - see the comment at the top of
each for how to run them by hand (they need the app already running).

---

## Troubleshooting

* **`NOAUTH Authentication required`** when running tests/local dev against
  the Docker Redis - you're pointing local tooling at the password-protected
  Redis from `docker-compose`. Use a separate unauthenticated Redis for local
  dev/tests (see Option B / test instructions above), or pass `-a changeme`
  to `redis-cli`.
* **App fails to start with an unresolved `${redis.host}` placeholder** - make
  sure `SPRING_PROFILES_ACTIVE` is set to `docker` (Docker) or nothing at all
  (defaults to `local`, which is checked into `application-local.yml`).
* **Port already in use** (8080/6379/9090/3000) - something else is already
  bound to it; stop that process or change the port mapping in
  `docker-compose.yml`.
