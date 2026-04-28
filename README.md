# DLQ Revive

> Kafka Dead Letter Queue Mutation & Redrive Engine

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Angular](https://img.shields.io/badge/Angular-21-red.svg)](https://angular.dev/)

## What is DLQ Revive?

When Kafka consumers fail due to schema changes, bad data, or downstream outages, messages pile up in **Dead Letter Queue (DLQ) topics**. DLQ Revive lets platform engineers:

1. **Connect** — Point at any Kafka cluster with a bootstrap server URL
2. **Browse** — View stuck DLQ messages with paginated, memory-safe browsing
3. **Transform** — Write JSONata expressions to map old schema → new schema
4. **Preview** — See before/after JSON side by side before committing
5. **Redrive** — Safely replay corrected messages to the target topic
6. **Audit** — Every action logged to PostgreSQL with full traceability

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 17, Spring Boot 3.2, Spring Kafka 3.x |
| Frontend | Angular 21, Angular Material |
| Database | PostgreSQL 15 |
| Messaging | Apache Kafka 3.x (Confluent Platform 7.5) |
| Transform | JSONata (declarative JSON-to-JSON — no code execution) |
| Build | Maven 3.8+, npm 11+, Docker Compose |

---

## Prerequisites

Before you begin, make sure you have the following installed:

| Tool | Version | Check Command |
|------|---------|--------------|
| **Java JDK** | 17+ | `java -version` |
| **Maven** | 3.8+ | `mvn -version` |
| **Node.js** | 18+ | `node -v` |
| **npm** | 9+ | `npm -v` |
| **Docker** | 24+ | `docker -v` |
| **Docker Compose** | v2+ (plugin) | `docker compose version` |
| **Git** | 2.x | `git --version` |

> **Note:** This project uses `docker compose` (v2 plugin), not the legacy `docker-compose` binary.

---

## Quick Start

### 1. Clone & Enter the Repo

```bash
git clone https://github.com/saifulhuq01/dlq-revive.git
cd dlq-revive
```

### 2. Start Infrastructure (Kafka + PostgreSQL)

```bash
docker compose -f docker/docker-compose.yml up -d
```

Wait for all containers to become **healthy** (~15–20 seconds):

```bash
docker ps --filter name=dlq-
```

You should see all three containers with `(healthy)` status:

```
NAMES           STATUS                  PORTS
dlq-kafka       Up 30 seconds (healthy) 0.0.0.0:9092->9092/tcp
dlq-postgres    Up 30 seconds (healthy) 0.0.0.0:5432->5432/tcp
dlq-zookeeper   Up 30 seconds (healthy) 0.0.0.0:2181->2181/tcp
```

### 3. Create a Test DLQ Topic & Produce Sample Messages

```bash
# Create the DLQ topic
docker exec dlq-kafka kafka-topics --create \
  --topic payments.dlq \
  --partitions 3 \
  --replication-factor 1 \
  --bootstrap-server localhost:9092

# Produce 10 sample failed messages
for i in $(seq 1 10); do
  echo "{\"orderId\": \"ORD-$i\", \"amount\": $((RANDOM % 1000)), \"status\": \"FAILED\", \"error\": \"Schema mismatch v2\"}" | \
  docker exec -i dlq-kafka kafka-console-producer \
    --topic payments.dlq \
    --bootstrap-server localhost:9092
done
```

### 4. Start the Backend

```bash
cd backend
mvn spring-boot:run
```

API is now available at **http://localhost:8080**

### 5. Start the Frontend

```bash
cd frontend
npm install
npx ng serve
```

Dashboard is now available at **http://localhost:4200**

---

## Project Structure

```
dlq-revive/
├── backend/                    # Spring Boot API
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/dlqrevive/
│       │   ├── DlqReviveApplication.java
│       │   ├── api/            # REST controllers
│       │   ├── connector/      # Kafka connection management
│       │   ├── reader/         # DLQ message reader (assign+seek)
│       │   ├── transform/      # JSONata transformation engine
│       │   ├── redrive/        # Idempotent redrive engine
│       │   └── audit/          # Audit trail logging
│       └── resources/
│           ├── application.yml # Spring Boot configuration
│           └── schema.sql      # PostgreSQL DDL
├── frontend/                   # Angular 21 dashboard
│   └── src/app/
│       ├── connect/            # Cluster connection UI
│       ├── browse/             # DLQ message browser
│       ├── transform/          # JSONata editor + preview
│       └── audit/              # Audit trail viewer
├── docker/
│   ├── docker-compose.yml      # Kafka + Zookeeper + PostgreSQL
│   └── .env.example            # Environment variable template
├── docs/
│   ├── getting-started.md
│   └── security.md
└── .github/workflows/ci.yml    # GitHub Actions CI pipeline
```

---

## Architecture

```
┌──────────────────────────────────────────┐
│         Angular 21 Dashboard             │
│  Connect │ Browse │ Transform │ Audit    │
└──────────────────┬───────────────────────┘
                   │ REST + SSE (paginated)
┌──────────────────▼───────────────────────┐
│         Spring Boot 3 API                │
│  JSONata Engine │ Idempotency │ Audit    │
└────┬──────────────────┬──────────┬───────┘
     │ assign()+seek()  │          │
┌────▼────┐    ┌────────▼──┐  ┌───▼────────┐
│ Kafka   │    │ Schema    │  │ PostgreSQL │
│ DLQ     │    │ Registry  │  │ redrive_log│
│ Topics  │    │ (v1.1)    │  │ templates  │
└─────────┘    └───────────┘  └────────────┘
```

---

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/dlq/{topic}/messages?partition=0&fromOffset=0&limit=50` | Browse DLQ messages (paginated, max 100) |
| POST | `/dlq/transform/preview` | Preview JSONata transformation |
| POST | `/dlq/redrive` | Execute redrive (max 100 msgs free) |
| GET | `/dlq/audit` | View audit trail |
| GET | `/dlq/templates` | List saved transform templates |
| POST | `/dlq/templates` | Save a transform template |

---

## Configuration

### Environment Variables

All sensitive configuration is managed through environment variables. **Never commit credentials to the repo.**

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/dlqrevive` | PostgreSQL JDBC URL |
| `DB_USERNAME` | `dlqrevive` | Database username |
| `DB_PASSWORD` | `dlqrevive` | Database password |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka bootstrap servers |
| `POSTGRES_DB` | `dlqrevive` | Docker Compose: database name |
| `POSTGRES_USER` | `dlqrevive` | Docker Compose: database user |
| `POSTGRES_PASSWORD` | `dlqrevive` | Docker Compose: database password |

### Running Backend Inside Docker

When the backend runs inside Docker (same network as Kafka/Postgres), set:

```bash
KAFKA_BOOTSTRAP_SERVERS=kafka:29092
DB_URL=jdbc:postgresql://postgres:5432/dlqrevive
```

See `docker/.env.example` for a complete template.

### Running Backend Locally (Outside Docker)

The defaults in `application.yml` work out of the box — Kafka on `localhost:9092` and PostgreSQL on `localhost:5432`.

---

## Docker Compose Details

The `docker/docker-compose.yml` provisions three services:

| Service | Image | Port | Health Check |
|---------|-------|------|-------------|
| **Zookeeper** | `confluentinc/cp-zookeeper:7.5.0` | 2181 | `nc -z localhost 2181` |
| **Kafka** | `confluentinc/cp-kafka:7.5.0` | 9092 (external), 29092 (internal) | `kafka-broker-api-versions` |
| **PostgreSQL** | `postgres:15-alpine` | 5432 | `pg_isready` |

### Kafka Listener Architecture

Kafka is configured with **two listeners** to solve the Docker networking problem:

- **INTERNAL** (`kafka:29092`) — Used by other containers on the Docker network
- **EXTERNAL** (`localhost:9092`) — Used by your host machine (backend running locally)

### Useful Docker Commands

```bash
# Start all services
docker compose -f docker/docker-compose.yml up -d

# Check health status
docker ps --filter name=dlq-

# View logs
docker logs dlq-kafka
docker logs dlq-postgres

# Stop all services
docker compose -f docker/docker-compose.yml down

# Stop and remove all data (fresh start)
docker compose -f docker/docker-compose.yml down -v
```

---

## Security Model

> Full details: [docs/security.md](docs/security.md)

### Transformation Security — JSONata Only

DLQ Revive uses **JSONata** exclusively for message transformations. This is a deliberate security decision.

| | JSONata | Groovy | JavaScript eval() |
|---|---------|--------|-------------------|
| System access | ❌ None | ✅ Full | ✅ Full |
| File access | ❌ None | ✅ Full | ✅ Full |
| Network access | ❌ None | ✅ Full | ✅ Full |
| RCE risk | ❌ Zero | 🔴 CRITICAL | 🔴 CRITICAL |

**Why this matters:** Many similar tools use Groovy or JS eval for transformations — this is effectively giving users Remote Code Execution as a feature. JSONata is purely declarative: it can only query and reshape JSON. It cannot execute system commands, read files, or make network calls.

### Kafka Consumer Security

- **View mode** uses `consumer.assign()` + `consumer.seek()` — never joins consumer groups, never commits offsets, cannot interfere with production consumers
- **Redrive mode** checks every message against `redrive_log` before producing — pod-restart safe, idempotent

### Credential Security

- No secrets in code — all credentials via environment variables
- PostgreSQL credentials via `DB_USERNAME` / `DB_PASSWORD`
- `.env` files are git-ignored; use `docker/.env.example` as a template
- No data leaves your infrastructure (fully self-hosted)

### Reporting Vulnerabilities

If you discover a security vulnerability, **do NOT open a public GitHub issue**.

Email: **security@dlqrevive.com** — we respond within 48 hours.

---

## Safety Guarantees

- **No Code Execution** — JSONata only. No Groovy, no JavaScript eval. Zero RCE surface.
- **Memory Safe** — All Kafka reads paginated (max 100 msgs/call). No full topic loads.
- **Idempotent Redrives** — Every message checked against `redrive_log` before producing. Pod-restart safe.
- **Non-Intrusive** — View mode uses `assign()+seek()`. Never joins consumer groups. Never commits offsets.

---

## Running Tests

### Backend

```bash
cd backend
mvn test
```

> **Important:** Run Maven from the `backend/` directory, not the project root. There is no root-level `pom.xml`.

### Frontend

```bash
cd frontend
npm test
```

---

## CI/CD

GitHub Actions runs on every push to `dev`/`week*`/`hotfix/*` branches and on PRs to `main`/`dev`. The pipeline:

1. Spins up a PostgreSQL service container
2. Builds the backend with Java 17 (Temurin)
3. Runs all Maven tests
4. Uploads test results as artifacts

See [`.github/workflows/ci.yml`](.github/workflows/ci.yml) for details.

---

## Troubleshooting

### `mvn test` says "no POM in this directory"

You're in the wrong directory. Run from `backend/`:

```bash
cd backend && mvn test
```

### Docker network error on first start

If you see `Network docker_default Error`, just run the command again — Docker sometimes fails to create the network on the first attempt:

```bash
docker compose -f docker/docker-compose.yml down
docker compose -f docker/docker-compose.yml up -d
```

### Kafka container keeps restarting

Check logs with `docker logs dlq-kafka`. Common causes:
- Zookeeper not healthy yet (the healthcheck-based `depends_on` should prevent this)
- Port 9092 already in use on your host

### Backend can't connect to Kafka inside Docker

Make sure you set `KAFKA_BOOTSTRAP_SERVERS=kafka:29092` (not `localhost:9092`) when the backend runs inside Docker. `localhost` inside a container refers to the container itself, not your host machine.

---

## Free Tier Limits

| Feature | Free (Self-Hosted) |
|---------|-------------------|
| DLQ Browse | ✓ Unlimited |
| JSONata Transform | ✓ Unlimited |
| Single Redrive | ≤ 100 messages |
| Saved Templates | 1 |
| Audit Log | Local PostgreSQL |

Need bulk redrive (>100 msgs), team workspaces, SSO, or compliance exports? → [DLQ Revive Cloud](https://dlqrevive.com)

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines, architecture rules, and code quality standards.

## License

MIT — see [LICENSE](LICENSE) for details.

---

Built by [Mohammed Saifulhuq](https://github.com/saifulhuq01)
