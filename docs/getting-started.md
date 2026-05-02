# Getting Started with DLQ Revive

## Prerequisites

- Java 17+
- Docker & Docker Compose v2 (plugin)
- Node.js 18+ (for Angular frontend)
- Maven 3.8+

## Quick Start

### 1. Start Infrastructure

```bash
docker compose -f docker/docker-compose.yml up -d
```

Wait for all containers to become healthy (~15–20 seconds):

```bash
docker ps --filter name=dlq-
```

This starts:
- **Zookeeper** on `localhost:2181`
- **Kafka** on `localhost:9092` (external) / `kafka:29092` (internal Docker network)
- **PostgreSQL** on `localhost:5432` (database: `dlqrevive`)

### 2. Create a Test DLQ Topic

```bash
docker exec dlq-kafka kafka-topics --create \
  --topic payments.dlq \
  --partitions 3 \
  --replication-factor 1 \
  --bootstrap-server localhost:9092
```

### 3. Produce Sample DLQ Messages

```bash
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

API is now available at `http://localhost:8080`

> **Important:** Always run Maven from the `backend/` directory. There is no root-level `pom.xml`.

### 5. Start the Frontend

```bash
cd frontend
npm install
npx ng serve
```

Dashboard is now available at `http://localhost:4200`

## Environment Configuration

When running the backend **locally** (outside Docker), the defaults work out of the box:
- `KAFKA_BOOTSTRAP_SERVERS` defaults to `localhost:9092`
- `DB_URL` defaults to `jdbc:postgresql://localhost:5432/dlqrevive`

When running the backend **inside Docker**, override with:
```bash
KAFKA_BOOTSTRAP_SERVERS=kafka:29092
DB_URL=jdbc:postgresql://postgres:5432/dlqrevive
```

See `docker/.env.example` for all available environment variables.

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/dlq/{topic}/messages?partition=0&fromOffset=0&limit=50` | Browse DLQ messages (paginated) |
| POST | `/dlq/transform/preview` | Preview JSONata transformation |
| POST | `/dlq/redrive` | Execute redrive (max 100 msgs free) |
| GET | `/dlq/audit` | View audit trail |
| GET | `/dlq/templates` | List saved templates |
| POST | `/dlq/templates` | Save a transform template |

## Example: JSONata Transformation

**Input (broken message):**
```json
{"orderId": "ORD-123", "amount": 500, "status": "FAILED"}
```

**JSONata Expression:**
```
{ "order_id": orderId, "total": amount, "state": "RETRY" }
```

**Output (fixed message):**
```json
{"order_id": "ORD-123", "total": 500, "state": "RETRY"}
```
