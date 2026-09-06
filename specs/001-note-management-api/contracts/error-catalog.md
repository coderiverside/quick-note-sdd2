# Error Catalog: Quick Note — Note Management API

**Date**: 2026-08-25 | **Contract**: [openapi.yaml](./openapi.yaml)

Every non-2xx response is `application/problem+json` per RFC 9457 and carries a `code`. Per the
constitution, **these codes are part of the contract**: a code may be added within a version, but its
meaning MUST NOT change and it MUST NOT be removed without a version bump.

`type` URIs resolve under `https://api.quicknote.example/problems/{code}`.

## Authentication and authorization

| Code | Status | Meaning | Notes |
|---|---|---|---|
| `unauthenticated` | 401 | No credential, or the credential failed verification | Covers missing, malformed, expired, wrong-issuer, wrong-audience, and bad-signature. Deliberately does **not** distinguish them (FR-003) |

**There is no `403` in this API.** Resource-ownership failures return `404` so that existence is not
disclosed (FR-008), and no operation defines a scope a valid credential could lack. If a future
version introduces scopes, `403` and its code are added then — not reserved now, because a code
nothing can emit is a contract entry that quietly rots.

## Not found

| Code | Status | Meaning |
|---|---|---|
| `note_not_found` | 404 | The note does not exist, is not owned by the caller, or (for `/trash/{noteId}`) is not in the trash |
| `label_not_found` | 404 | The label does not exist or is not owned by the caller |

Ownership failure and non-existence are indistinguishable by design. Returning `403` for
another user's note would confirm that note exists.

## Validation

| Code | Status | Meaning |
|---|---|---|
| `validation_failed` | 400 | One or more fields failed validation. Carries the `errors` array with one entry per offending field |
| `note_content_required` | 400 | Neither `title` nor `body` was supplied, or the update would leave both empty (FR-010) |
| `note_color_invalid` | 400 | Color is outside the palette. `detail` lists the permitted values (FR-018) |
| `malformed_identifier` | 400 | A path or query identifier is not a well-formed UUID. Rejected before any lookup |
| `malformed_request` | 400 | The body is not valid JSON, or carries unknown properties |

### `errors[].code` values

Used inside the `errors` array of a `validation_failed` response.

| Code | Meaning |
|---|---|
| `required` | Field is required but absent |
| `too_long` | Exceeds the maximum length; `message` names the limit |
| `blank` | Present but empty or whitespace-only (FR-015) |
| `out_of_range` | Numeric value outside its permitted range, e.g. `size` above 100 |
| `invalid_format` | Value does not match the expected format |
| `too_many_items` | Array exceeds its maximum size |

## Conflict

| Code | Status | Meaning |
|---|---|---|
| `label_name_conflict` | 409 | A label with this name already exists for the caller, compared case-insensitively after trimming (FR-027) |
| `note_trashed` | 409 | The note is in the trash; content edits, pin, archive, and label changes are refused until it is restored (FR-021) |
| `note_version_conflict` | 409 | A concurrent update won. The client should re-read and retry |

## Unprocessable

| Code | Status | Meaning |
|---|---|---|
| `note_label_limit_exceeded` | 422 | The note already carries the maximum of 20 labels (FR-030) |

## Payload limits

| Code | Status | Meaning |
|---|---|---|
| `payload_too_large` | 413 | The request body exceeds the transport byte ceiling. Rejected at the edge before any handler runs, so no partial state is written |

This is an abuse guard, not the content limit, and the distinction matters. The `title` and `body`
limits are counted in **characters** and are enforced by validation, which returns
`400 validation_failed` naming the field. The byte ceiling sits far above the largest legitimate
note, so a well-formed client never receives a `413` — and in particular a 20,000-character note in a
script that encodes to four bytes per character is valid, not oversized.

## Rate limiting

| Code | Status | Meaning |
|---|---|---|
| `rate_limited` | 429 | Per-principal or per-IP limit exceeded. `Retry-After` gives the wait in seconds |

## Server

| Code | Status | Meaning |
|---|---|---|
| `internal_error` | 500 | An unhandled fault. The body carries only `code`, `title`, `status`, and `correlationId` — never a stack trace, SQL fragment, hostname, or upstream error text (FR-043). The full detail is logged against the same correlation ID |
| `service_unavailable` | 503 | A critical dependency is unavailable. Also what the readiness probe reflects |

## Example

```json
{
  "type": "https://api.quicknote.example/problems/validation_failed",
  "title": "Validation failed",
  "status": 400,
  "detail": "The request contains 2 invalid fields.",
  "instance": "/v1/notes",
  "code": "validation_failed",
  "correlationId": "b7f3c1a4-9e2d-4f18-8a55-2c0d6e1f4b93",
  "errors": [
    { "field": "title", "code": "too_long", "message": "must be at most 200 characters" },
    { "field": "color", "code": "invalid_format", "message": "must be one of: default, red, orange, yellow, green, teal, blue, dark_blue, purple, pink, brown" }
  ]
}
```
