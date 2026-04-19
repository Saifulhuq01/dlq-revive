# Security Model — DLQ Revive

## Transformation Security

DLQ Revive uses **JSONata** exclusively for message transformations.

### Why JSONata (and NOT Groovy/JavaScript)

| | JSONata | Groovy | JavaScript eval() |
|---|---------|--------|-------------------|
| System access | ❌ None | ✅ Full | ✅ Full |
| File access | ❌ None | ✅ Full | ✅ Full |
| Network access | ❌ None | ✅ Full | ✅ Full |
| RCE risk | ❌ Zero | 🔴 CRITICAL | 🔴 CRITICAL |
| Turing-complete | ❌ No | ✅ Yes | ✅ Yes |

JSONata is a **purely declarative** JSON query and transformation language. It cannot:
- Execute system commands
- Read/write files
- Make network requests
- Access environment variables
- Import libraries

This is not a limitation — it's the entire point.

## Kafka Consumer Security

### View Mode (Browsing DLQ)
- Uses `consumer.assign()` + `consumer.seek()` — does NOT join consumer groups
- Never calls `commitSync()` or `commitAsync()`
- Cannot interfere with production consumers
- Read-only: no state is modified on the Kafka cluster

### Redrive Mode
- Only activated by explicit user confirmation
- Every message checked against `redrive_log` before producing
- Idempotent: pod restart cannot cause double-redrive
- Producer uses `acks=all` for strongest durability guarantee

## Data Security

- No API keys or secrets in code — use environment variables
- PostgreSQL credentials via `DB_USERNAME` / `DB_PASSWORD` env vars
- No data leaves the user's infrastructure (self-hosted)
- Audit trail logs every action with user identity and timestamp

## Reporting Vulnerabilities

If you discover a security vulnerability, **do NOT open a public GitHub issue**.

Email: security@dlqrevive.com

We will respond within 48 hours and coordinate disclosure.
