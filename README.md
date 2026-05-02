# DLQ Revive

Kafka Dead Letter Queue recovery that doesn't require a throwaway script.

![DLQ Revive Demo](docs/media/demo.gif)

## The Problem

We posted on Reddit asking how engineers deal with failed Kafka messages, and the response was unanimous: everyone hates writing one-off throwaway scripts just to parse and replay dead letters. Developers waste hours writing custom consumers and producers for every new DLQ scenario, risking production data with untested scripts. DLQ Revive was built to provide a permanent, safe, and visual interface to inspect, transform, and redrive these messages instantly.

## Quick Start

```bash
docker compose up --build
```
Open [http://localhost:4200](http://localhost:4200) in your browser.

## What It Does

* **Connect** — Point at any Kafka cluster with a bootstrap server URL.
* **Browse** — View stuck DLQ messages with paginated, memory-safe browsing.
* **Transform** — Write JSONata expressions to map old schema to new schema seamlessly.
* **Preview** — See before/after JSON side by side before committing changes.
* **Redrive** — Safely replay corrected messages to the target topic with built-in idempotency constraints.
* **Audit** — Every action is strictly logged to PostgreSQL with full traceability and session monitoring.

## Architecture

```text
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

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines, architecture rules, and code quality standards.

## License

MIT
