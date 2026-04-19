# DLQ Revive

> Kafka Dead Letter Queue Mutation & Redrive Engine

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)

## What is DLQ Revive?

When Kafka consumers fail due to schema changes, bad data, or downstream outages, messages pile up in **Dead Letter Queue (DLQ) topics**. DLQ Revive lets platform engineers:

1. **Browse** — View stuck DLQ messages with paginated, memory-safe browsing
2. **Transform** — Write JSONata expressions to map old schema → new schema
3. **Preview** — See before/after JSON side by side before committing
4. **Redrive** — Safely replay corrected messages to the target topic
5. **Audit** — Every action logged to PostgreSQL with full traceability

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 17, Spring Boot 3.x, Spring Kafka 3.x |
| Frontend | Angular 13+, Angular Material |
| Database | PostgreSQL 15 |
| Messaging | Apache Kafka 3.x (plain JSON topics in v1) |
| Transform | JSONata (declarative JSON-to-JSON — no code execution) |
| Build | Maven, Docker Compose |

## Quick Start

```bash
# Clone the repo
git clone https://github.com/saifulhuq01/dlq-revive.git
cd dlq-revive

# Start all services (Kafka, PostgreSQL, Backend, Frontend)
docker compose up -d

# Open the dashboard
open http://localhost:4200
```

## Architecture

```
┌──────────────────────────────────────────┐
│         Angular 13+ Dashboard            │
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

## Safety Guarantees

- **No Code Execution** — JSONata only. No Groovy, no JavaScript eval. Zero RCE surface.
- **Memory Safe** — All Kafka reads paginated (max 100 msgs/call). No full topic loads.
- **Idempotent Redrives** — Every message checked against `redrive_log` before producing. Pod-restart safe.
- **Non-Intrusive** — View mode uses `assign()+seek()`. Never joins consumer groups. Never commits offsets.

## Free Tier Limits

| Feature | Free (Self-Hosted) |
|---------|-------------------|
| DLQ Browse | ✓ Unlimited |
| JSONata Transform | ✓ Unlimited |
| Single Redrive | ≤ 100 messages |
| Saved Templates | 1 |
| Audit Log | Local PostgreSQL |

Need bulk redrive (>100 msgs), team workspaces, SSO, or compliance exports? → [DLQ Revive Cloud](https://dlqrevive.com)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## License

MIT — see [LICENSE](LICENSE) for details.

---

Built by [Mohammed Saifulhuq](https://github.com/saifulhuq01)
