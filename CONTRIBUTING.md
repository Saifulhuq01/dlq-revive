# Contributing to DLQ Revive

Thank you for your interest in contributing to DLQ Revive!

## Getting Started

1. Fork the repository
2. Clone your fork: `git clone https://github.com/YOUR_USERNAME/dlq-revive.git`
3. Create a feature branch: `git checkout -b feat/your-feature`
4. Make your changes
5. Run tests: `cd backend && mvn test`
6. Commit: `git commit -m "feat: describe your change"`
7. Push: `git push origin feat/your-feature`
8. Open a Pull Request

## Architecture Rules (Non-Negotiable)

Before contributing code, you **must** understand these rules. PRs violating them will be rejected immediately:

### 1. JSONata Only for Transformations
- **NEVER** use Groovy, JavaScript `eval()`, or any Turing-complete language for user-provided transformations
- Use `Jsonata.of(expression).evaluate(jsonNode)` — that's the entire transform engine
- Why: Groovy/JS = Remote Code Execution vulnerability as a feature

### 2. Paginated Kafka Reads
- **NEVER** load all messages from a topic into memory
- API enforces `limit` parameter (max 100)
- Use `consumer.seek(partition, fromOffset)` and stop after `limit` records

### 3. Idempotent Redrives
- Before producing any message: check `redrive_log` table
- If offset already redriven → skip
- INSERT log record before producing
- This prevents double-redrive on pod restart

### 4. No subscribe() in View Mode
- View mode uses `consumer.assign()` + `consumer.seek()` ONLY
- **NEVER** call `subscribe()`, `commitSync()`, or `commitAsync()` in view mode
- These steal partitions and corrupt consumer group state

### 5. Code Quality
- No `@SuppressWarnings("unchecked")`
- No `System.out.println` — use SLF4J
- All Kafka consumers wrapped in try-finally for `close()`
- All write operations use `@Transactional`
- Error responses use RFC 7807 ProblemDetail format

## What Belongs Here (and What Doesn't)

| ✅ Belongs in this repo | ❌ Does NOT belong |
|------------------------|-------------------|
| Kafka connection/read | SSO/SAML code |
| JSONata transformation | Stripe billing |
| Single redrive (≤100) | Multi-tenancy |
| Idempotency guard | Team roles |
| Audit logging | Bulk redrive (>100) |
| Docker Compose setup | Terraform/K8s |

Enterprise and SaaS features are maintained in a separate private repository.

## Reporting Issues

- Use GitHub Issues
- Include: Kafka version, Spring Boot version, steps to reproduce
- For security vulnerabilities: email security@dlqrevive.com (do NOT open a public issue)

## Code of Conduct

Be respectful. Be constructive. Focus on the code, not the person.
