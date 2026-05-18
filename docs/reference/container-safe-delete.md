---
layout: default
title: Container safe-delete (reference)
permalink: /reference/container-safe-delete/
---

# Container safe-delete

The `/v2/{kind}-containers/{id}` `DELETE` endpoints refuse to delete
a container that still has active references unless the caller passes
`?force=true`. They exist on this fork as a server-enforced safety
layer; the upstream `/shepard/api/{kind}Containers/{id}` `DELETE`
keeps its original "always succeed, silently orphan surviving
references" semantics so existing clients keep working.

## When to use them

Pick the `/v2/` endpoint when you want the server to push back on a
delete that would leave behind unreachable data. The shepard frontend
already does this and adds an in-dialog warning, but any non-UI client
— an admin script, a Jupyter notebook, a CI cleanup job — should
prefer the `/v2/` variant for the same protection.

The legacy `/shepard/api/` `DELETE` is preserved verbatim for two
reasons:

- Upstream wire compatibility (`/shepard/api/...` is frozen on this
  fork — see `CLAUDE.md`).
- Some operational workflows really do want to nuke a container
  even when it has references, e.g. when wiping a misconfigured
  storage backend before re-importing data. Pass `?force=true` on
  the `/v2/` endpoint to get the same behaviour with explicit intent.

## Endpoints

All three kinds follow the same shape:

| Method | Path |
|---|---|
| `DELETE` | `/v2/timeseries-containers/{id}` |
| `DELETE` | `/v2/file-containers/{id}` |
| `DELETE` | `/v2/structured-data-containers/{id}` |

### Query parameters

| Name | Default | Effect |
|---|---|---|
| `force` | `false` | When `true`, skip the reference check and delete the container even if references exist. |

### Responses

| Status | When | Body |
|---|---|---|
| **204** | Deleted (no references, or `force=true` was passed). | empty |
| **409** | Container still has active references and `force` was not set. | `SafeDeleteConflict` (below) |
| **401** | No bearer token / API key. | RFC 7807 problem |
| **403** | Caller lacks Write permission on the container. | RFC 7807 problem |
| **404** | No container with that id (or already deleted). | RFC 7807 problem |

### `SafeDeleteConflict` body

```json
{
  "referenceCount": 3,
  "sampleDataObjectAppIds": [
    "01HX1ABCDE...",
    "01HX1AABCD...",
    "01HX1AAABC..."
  ]
}
```

- **`referenceCount`** — total number of non-deleted data objects that
  reference this container.
- **`sampleDataObjectAppIds`** — first ≤ 10 referencing data objects
  by application id; useful for telling the user *which* datasets
  would be orphaned. The list is capped at 10 to keep the response
  small for containers with thousands of references.

## Worked example

```bash
# Try to delete a container that has references — server refuses.
curl -X DELETE \
  -H "Authorization: Bearer $TOKEN" \
  https://shepard.example.dlr.de/v2/timeseries-containers/42
# → 409 Conflict
# {"referenceCount":7,"sampleDataObjectAppIds":["01HX...","01HY...",...]}

# After confirming with the user, retry with ?force=true.
curl -X DELETE \
  -H "Authorization: Bearer $TOKEN" \
  "https://shepard.example.dlr.de/v2/timeseries-containers/42?force=true"
# → 204 No Content
```

## Frontend behaviour

The container detail pages in the shepard frontend always pass
`force=true` because the page has already shown a warning naming the
referencing data objects in the type-the-name confirm dialog. The
`/v2/` endpoint is what the page calls — the user-facing wire is
already on the safe shelf.

## Related

- [Container-level semantic annotations](/reference/container-annotations/) —
  the other new `/v2/` surface that uses the same container detail pages.
- [Permissions](/admin/) — the standard Write check applies; users
  without Write permission get 403 from both the safe-delete and the
  legacy endpoints.
