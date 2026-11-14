# property-notification-api

> notification bounded context: service API

Part of the **Pms_Property** distributed system, Grupo 2. Governance and
documentation live in [`property-docs`](https://github.com/code-corhuila/property-docs).

Hexagonal service of the **notification** domain, in Java 21 and Spring Boot 3.5, as a
three-module Maven build (ADR-011, Annex C). Its only REST surface is the
notification preferences of each user (E-16, E-17, HU-NOTIF-002); the consumer of
Booking's events that sends the messages is of tanda 3.

| Operation | Fiche | What it does |
|---|---|---|
| `GET /api/v1/preferencias` | E-16 | The channels of the user of the token; the defaults (e-mail and push on, SMS off) if never saved |
| `PATCH /api/v1/preferencias` | E-17 | Changes only the channels sent (`email`, `sms`, `push`, each `true` or `false`) and returns all three |
| `GET /health` | — | Answers without a token, outside `/api/v1` |

Own only: the preferences are always those of the account in the token, whatever its
role; a service credential gets `403`.

```
notification-core/       domain + application — NO Spring dependency
notification-adapters/   Spring Web adapters
  adapter/in/http/         RS256 verifier, auth and correlation filters, error envelope, /health
  adapter/out/persistence/ the schema notification (JDBC); an in-memory twin only with PERSISTENCE=memory
notification-app/        Spring Boot application: the composition root
deploy/                  Dockerfile and this service's compose file
```

**Why three modules.** `notification-core` does not declare Spring as a dependency,
so a `@Service` or `@Entity` in the domain **does not compile** (norm 5.3.3).

The schema lives in `property-notification-db`; this service never migrates it. In
the platform it connects as `notification_app` (Annex J, ADR-008).

## The consumer of the outcome of a reservation (HU-NOTIF-001)

The same jar started with `--reservas-consumer` (service `notification-consumer` in
`deploy/compose.yml`) reads `notification.reservas` as the broker user `notification` and tells
the guest how each Reserva ended (`ReservaConfirmada`, `ReservaCancelada`, `ReservaExpirada`):

1. Once per event: an `eventId` already in `processed_event` changes nothing.
2. With e-mail on in the guest's preferences (no row = the defaults), it reads the address from
   Identity's `GET /users/{id}/contact` (E-26) with its own token `svc-notification`, renders
   the template of the event (`message_template`, seeded by `property-notification-db`) and sends
   it by SMTP to `MAIL_HOST`. **Only e-mail is sent in MVP 1**: SMS and push keep the preference
   but send and record nothing (DEC-006). An account Identity does not know (`404`) has no
   address: nothing is sent.
3. The `delivery_log` row and the `eventId` are written in one transaction, then the message is
   acknowledged. Identity, the database or the mail server failing: retried after 1 s, 2 s and
   4 s, then rejected to `notification.reservas.dlq` (7 days). An unreadable message goes there
   at once.

Neither the e-mail nor the body of a message is ever logged or stored here; each line carries
the `correlationId` of the event. In `develop`, the test mail server of `--profile dev` in
`property-infra-postgres` receives the messages.

## Run

```bash
mvn -B verify                                   # build and every test
mvn -B -DskipTests package
java -jar notification-app/target/notification-app-0.1.0.jar
```

Without Maven installed:

```bash
docker run --rm -v "$PWD":/app -w /app maven:3.9-eclipse-temurin-21 mvn -B verify
```

### Storage and the integration tests

The service stores the preferences in the schema `notification`, connecting as
`notification_app` (`.env.example` lists the variables); without `DATABASE_URL` it does
not start. The in-memory twin runs only with `PERSISTENCE=memory`: never in an
environment (norm 5.5.2). The integration tests run against the real engine with the
schema of `property-notification-db` applied, only when `TEST_DATABASE_URL` is set
(Annex C). Locally, with `property-notification-db` cloned as a sibling:

```bash
scripts/test-db.sh            # PostgreSQL 16 on 127.0.0.1:55436 with the notification schema
TEST_DATABASE_URL=jdbc:postgresql://localhost:55436/property TEST_DATABASE_USER=notification_app \
  TEST_DATABASE_PASSWORD=test-only mvn -B verify
scripts/test-db.sh down
```

The image:

```bash
docker build -f deploy/Dockerfile -t notification-api .
```

Inside the network `platform` the service is `http://notification-api:3005`; from
outside it is reached only through the gateway, at `http://localhost:8080/api/v1/preferencias`.

## Branching

Three permanent branches. **None of them accepts a direct commit** — you enter through a child
branch and leave through a Pull Request.

```
develop  <--PR--  feat/... fix/... chore/...
qa       <--PR--  hu-<story>-qa
main     <--PR--  release/...  hotfix/...
```

Promotion happens **by re-application** (`git cherry-pick -x`), never by merging one permanent
branch into another: `merge develop -> qa` and `merge qa -> main` do not exist in this model.

`main` requires **1 approval from `ariel5253`**. On `develop` and `qa` the team sets its own review
rule.

Full policy: `00-governance/branching-policy.md` and `00-governance/git-conventions.md` in
`property-docs`.
