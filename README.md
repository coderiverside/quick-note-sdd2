# Quick Note — Note Management API

A personal note-taking REST API: capture notes, organize them with pin/archive state, colours and
user-defined labels, find them by search, and recover deleted ones from a 30-day trash.

Governed by [the project constitution](.specify/memory/constitution.md). The
[HTTP contract](specs/001-note-management-api/contracts/openapi.yaml) is hand-authored and
authoritative — where the running service and that document disagree, the document is correct and
the service is defective.

## Running locally

```bash
mvn quarkus:dev          # Dev Services starts PostgreSQL and Keycloak; nothing to install
docker compose up --build # the packaged stack, migrations as a discrete step
```

See [quickstart.md](specs/001-note-management-api/quickstart.md) for validation scenarios.

## The credential contract this service verifies

Quick Note **verifies** credentials and never issues them. It has no login, no token, no refresh and
no password-reset route, stores no password, and holds no session — `CredentialScopeTest` fails the
build if any of that changes. Issuing credentials is the identity provider's job.

Present a bearer JWT from the configured realm. The service checks:

| Claim / property | Expectation |
|---|---|
| Signature | RS256, verified against the realm's published JWKS (rotation handled automatically) |
| `iss` | The realm named by `QUARKUS_OIDC_AUTH_SERVER_URL` |
| `aud` / `azp` | The client named by `QUARKUS_OIDC_CLIENT_ID` (default `quicknote-api`) |
| `exp` | Not expired |
| `sub` | **The only source of identity.** Notes and labels are owned by this value |

A user identifier supplied anywhere else — request body, query string, or another header — is ignored;
there is no code path that reads one.

Keycloak 24 and later carry `sub` in the **`basic`** client scope. A client without that scope issues
tokens with no subject, which this service correctly refuses as unauthenticated. The realm in
`docker/keycloak/quicknote-realm.json` assigns it.

Every failure — missing, malformed, expired, wrong issuer, wrong audience, bad signature — answers an
identical `401 unauthenticated`. Which check failed is deliberately not disclosed.

## Repository settings a maintainer must enable

The build enforces what a build can enforce. These three constitution rules are repository
administration and **cannot** be set from the build — enable them once on the hosting platform:

- **Require a pull request before merging** — direct pushes to `main` are prohibited
- **Require at least one approving review** from someone other than the author
- **Require all status checks to pass** — every job in [`ci.yml`](.github/workflows/ci.yml)

Conventional Commits are enforced two ways that *are* automated: a `commit-msg` hook, and a CI job
that lints every commit in a pull request. Enable the hook once per clone:

```bash
git config core.hooksPath .githooks
```
