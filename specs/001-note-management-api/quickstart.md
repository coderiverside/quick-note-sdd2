# Quickstart & Validation Guide: Quick Note — Note Management API

**Date**: 2026-08-25 | **Plan**: [plan.md](./plan.md) | **Contract**: [contracts/openapi.yaml](./contracts/openapi.yaml)

How to run the service, and the scenarios that prove the feature works end to end. This is a run and
validation guide — implementation code, entity bodies, migrations, and test suites belong to
`tasks.md` and the implementation phase.

---

## Prerequisites

| Tool | Version | Why |
|---|---|---|
| JDK | 25 (Temurin or equivalent) | Build and run target |
| Maven | 3.9.6+ | Build |
| Docker | 24+, with Compose v2 | Dev Services containers and the packaged stack |

Nothing else needs installing. Quarkus Dev Services starts PostgreSQL and Keycloak on demand, so
there is no local database to provision and no credentials to check in.

---

## Run for development

```bash
./mvnw quarkus:dev
```

This starts the service on `http://localhost:8080` and, through Dev Services, a throwaway PostgreSQL
and a Keycloak with a seeded `quicknote` realm. Live reload is active; the Dev UI is at
`http://localhost:8080/q/dev`.

Expected on startup: the log reports both Dev Services containers, Flyway reports the migrations it
applied, and the health endpoints answer.

```bash
curl -s localhost:8080/q/health/ready | jq .
# {"status":"UP","checks":[{"name":"Database connections health check","status":"UP"}, ...]}
```

## Run the packaged stack

```bash
./mvnw package
docker compose up --build
```

Compose brings up PostgreSQL on host port 5432, Keycloak on host port 8180 importing
`docker/keycloak/quicknote-realm.json`, a one-shot Flyway migration service, and the application on
host port 8080 — which starts only after the migration service exits successfully. These host ports
are pinned, and the `dev` profile pins the Dev Services Keycloak to the same 8180, so the token
command below works verbatim against either the Compose stack or `quarkus:dev`. The Dev Services
database port is left dynamic on purpose, so parallel test runs cannot collide. This is the
arrangement that satisfies the constitution's rule that migrations run as a discrete deploy step
rather than at application start; `quarkus.flyway.migrate-at-start` is `false` in the `prod` profile.

---

## Obtain a token

Every endpoint requires a Bearer JWT. Against the local Keycloak:

```bash
TOKEN=$(curl -s -X POST \
  "http://localhost:8180/realms/quicknote/protocol/openid-connect/token" \
  -d grant_type=password -d client_id=quicknote-api \
  -d username=alice -d password=alice | jq -r .access_token)
```

A second seeded user, `bob`, exists specifically so isolation can be verified by hand.

---

## Validation scenarios

Each scenario maps to a user story in [spec.md](./spec.md) and is also covered by an automated test.
The manual form here is for confirming a running deployment.

### 1. Capture and revisit (User Story 1, P1)

```bash
# Create
curl -si localhost:8080/v1/notes -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -H "Idempotency-Key: $(uuidgen)" \
  -d '{"title":"Groceries","body":"milk, bread"}'
```

**Expect**: `201`, a `Location` header, a body matching the `Note` schema with `pinned:false`,
`archived:false`, `trashed:false`, `color:"default"`, and equal `createdAt`/`updatedAt`.

```bash
# List, retrieve, update
curl -s "localhost:8080/v1/notes" -H "Authorization: Bearer $TOKEN" | jq .
curl -s "localhost:8080/v1/notes/$NOTE_ID" -H "Authorization: Bearer $TOKEN" | jq .
curl -s -X PATCH "localhost:8080/v1/notes/$NOTE_ID" -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"body":"milk, bread, coffee"}' | jq .
```

**Expect**: the note appears in the page; the update returns `200` with an `updatedAt` later than
`createdAt`.

**Rejections to confirm**:

```bash
curl -s -o /dev/null -w '%{http_code}\n' -X POST localhost:8080/v1/notes \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{}'          # 400
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/v1/notes                          # 401
```

The `400` body must carry `code: "note_content_required"`; the `401` must not reveal which
verification check failed.

### 2. Organize (User Story 2, P2)

Pin one note, archive another, colour a third — all via `PATCH /v1/notes/{id}`.

**Expect**: pinned notes sort ahead of unpinned in `GET /v1/notes`; the archived note disappears from
the default listing and appears under `?state=archived`; archiving a pinned note returns it with
`pinned:false`; a colour outside the palette returns `400 note_color_invalid` listing the permitted
values.

### 3. Labels (User Story 3, P3)

```bash
curl -s -X POST localhost:8080/v1/labels -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"name":"work"}'
curl -s -X PUT "localhost:8080/v1/notes/$NOTE_ID/labels/$LABEL_ID" -H "Authorization: Bearer $TOKEN"
curl -s "localhost:8080/v1/notes?labelId=$LABEL_ID" -H "Authorization: Bearer $TOKEN" | jq .
```

**Expect**: creating `WORK` after `work` returns `409 label_name_conflict`; attaching the same label
twice returns `204` both times with an unchanged note; renaming the label leaves note content
untouched; deleting the label returns `204` and the notes survive without it.

### 4. Search (User Story 4, P4)

```bash
curl -s "localhost:8080/v1/notes?q=COFFEE" -H "Authorization: Bearer $TOKEN" | jq '.items|length'
curl -s -o /dev/null -w '%{http_code}\n' "localhost:8080/v1/notes?size=500" -H "Authorization: Bearer $TOKEN"
```

**Expect**: the case-mismatched search matches; a search matching nothing returns `200` with an empty
`items` array, not `404`; `size=500` returns `400`, not a clamped page.

### 5. Trash (User Story 5, P5)

```bash
curl -s -X DELETE "localhost:8080/v1/notes/$NOTE_ID" -H "Authorization: Bearer $TOKEN" -o /dev/null -w '%{http_code}\n'
curl -s localhost:8080/v1/trash -H "Authorization: Bearer $TOKEN" | jq .
curl -s -X PATCH "localhost:8080/v1/notes/$NOTE_ID" -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"trashed":false}' | jq .
```

**Expect**: `204` on delete; the note appears in `/v1/trash` with `trashedAt`; restoring returns it
with title, body, colour, and labels intact, and to `archived` if it was archived when trashed;
editing a trashed note returns `409 note_trashed`; `DELETE /v1/trash/{noteId}` is irreversible and a
subsequent `GET` returns `404`.

### 6. Isolation (FR-007, FR-008 — the highest-severity check)

```bash
BOB=$(curl -s -X POST "http://localhost:8180/realms/quicknote/protocol/openid-connect/token" \
  -d grant_type=password -d client_id=quicknote-api -d username=bob -d password=bob | jq -r .access_token)
curl -s -o /dev/null -w '%{http_code}\n' "localhost:8080/v1/notes/$NOTE_ID" -H "Authorization: Bearer $BOB"
```

**Expect**: `404` — not `403`, which would confirm the note exists. Repeat for every operation that
takes a `noteId` or `labelId`.

---

## Automated verification

```bash
./mvnw verify
```

This runs, and must all pass before a merge:

| Gate | What runs | Fails on |
|---|---|---|
| Unit | Domain invariants, no container, no database | Any invariant regression |
| Architecture | ArchUnit layer rules | Domain importing JPA, Quarkus, or Jakarta REST; JPA entities escaping `infrastructure` |
| Integration | `@QuarkusTest` against Dev Services PostgreSQL + Keycloak | Any behavioural regression |
| Contract | Every integration response validated against `contracts/openapi.yaml` | A response shape absent from the contract |
| Contract drift | Generated OpenAPI compared to the authored document | Divergence — the authored file is correct, the code is the defect |
| Coverage | Jacoco | Below 80% lines overall, or below 100% in `security/` and validation paths |
| Vulnerabilities | OWASP Dependency-Check | Any high or critical advisory |

### Performance check

```bash
# 1,000 notes seeded for one user; measure first-page latency (SC-002)
./mvnw verify -Pperf
```

**Expect**: p95 under 1 s for the first page of a 1,000-note collection, and p95 under 300 ms for
single-note reads. Run `EXPLAIN ANALYZE` on the listing and search queries when investigating a
miss — a sequential scan means an index is missing, which the constitution treats as a defect rather
than a tuning opportunity.

---

## Configuration

All configuration comes from the environment and is validated at startup; the service refuses to
start on a missing or invalid required value. See `.env.example` for the full documented set.

| Variable | Purpose | Notes |
|---|---|---|
| `QUARKUS_DATASOURCE_JDBC_URL` | PostgreSQL URL | Unset in dev/test — Dev Services supplies it |
| `QUARKUS_DATASOURCE_USERNAME` / `_PASSWORD` | Database credentials | From the secret store in production; never committed |
| `QUARKUS_OIDC_AUTH_SERVER_URL` | Keycloak realm URL | Required |
| `QUARKUS_OIDC_CLIENT_ID` | Expected token audience | Required |
| `QUICKNOTE_TRASH_RETENTION_DAYS` | Trash retention | Default 30 |
| `QUICKNOTE_RATELIMIT_PER_PRINCIPAL` | Requests per minute per user | Instance-local; see the deviation in plan.md |
| `QUICKNOTE_RATELIMIT_PER_IP` | Requests per minute per source IP | Instance-local |

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| Dev Services does not start | Docker daemon not running, or port 8180 already bound — Keycloak is pinned there in `dev`; the database port is assigned dynamically |
| Every request returns `401` | Token issued by a different realm, or `QUARKUS_OIDC_CLIENT_ID` does not match the token `aud` |
| Search returns nothing for a substring that exists | `pg_trgm` extension missing — check that the initial notes migration ran |
| Search is slow | Trigram index missing; confirm with `EXPLAIN ANALYZE` |
| Trashed notes never purge | Quartz clustered tables absent, or the scheduler is disabled in this profile |
