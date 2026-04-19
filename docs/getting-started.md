# Getting Started with DLQ Revive

## Prerequisites

- Java 17+
- Docker & Docker Compose
- Node.js 18+ (for Angular frontend)
- Maven 3.8+

## Quick Start

### 1. Start Infrastructure

```bash
cd docker
docker compose up -d
```

This starts:
- **Kafka** on `localhost:9092`
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

### 5. Start the Frontend

```bash
cd frontend
npm install
ng serve
```

Dashboard is now available at `http://localhost:4200`

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
