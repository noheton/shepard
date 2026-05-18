---
layout: default
title: API and clients
permalink: /reference/api/
---

# API surfaces and clients

shepard exposes **two API shelves**, with a different stability promise
on each, and **three** sanctioned ways to build a client. This page is
the decision tree for "which one should I use?"

## The two API shelves

| Shelf | Path prefix | Stability | OpenAPI doc | Lives forever? |
|---|---|---|---|---|
| **Upstream-compatible** | `/shepard/api/...` | **Byte-frozen** vs `gitlab.com/dlr-shepard/shepard 5.2.0` | `/shepard/doc/openapi/v1.json` (P4c) | Yes — never breaking |
| **Fork development** | `/v2/...` | Evolving; this fork's additive shelf | `/shepard/doc/openapi/v2.json` (P4c) | Yes — own evolution path |

A client built against upstream shepard keeps working against this
fork on the `/shepard/api/...` surface. The `/v2/...` shelf is where
this fork's additions land (new payload kinds, output profiles, MCP
extensions — see [`aidocs/56`](https://github.com/noheton/shepard/blob/main/aidocs/56-v2-api-simplification-output-profiles-mcp.md)).

The combined `/shepard/doc/openapi.json` doc still serves the union
unchanged; the per-shelf split (`v1.json`, `v2.json`) is additive and
exists so each client-generation pipeline can target exactly the
surface it's best at.

### How shelf membership shows up in the docs

Every operation in the **combined** OpenAPI spec carries an inline
shelf badge so you don't have to read the URL to know which surface
you're on:

| Badge | Meaning |
|---|---|
| `[v1] …` in the operation summary | Lives on the `/shepard/api/...` upstream-frozen shelf |
| `[v2] …` in the operation summary | Lives on the `/v2/...` fork-development shelf |
| `[platform] …` | Server-internal (`/healthz`, `/openapi`, …) — not part of either shelf |

Machine readers (custom doc renderers, the Kiota generator) can branch
on the `x-shepard-shelf: v1 \| v2 \| platform` vendor extension that
the same filter writes on every `PathItem`, instead of string-parsing
the summary.

Endpoints never appear on both shelves — the path prefix is the
authoritative split. When a feature exists on both (rare: e.g. a
read-only legacy GET kept identical on `/shepard/api` and republished
under `/v2/` with a new payload shape), the docs show two separate
operations, each tagged with its own shelf badge.

## Choosing a client generator

Three sanctioned paths. The right one depends on what you're calling
and how much ceremony you want.

### 1. Calling `/v2/` endpoints — use the Kiota clients at `clients-v2/`

This is the **new baseline** ([ADR-0022](https://github.com/noheton/shepard/blob/main/aidocs/63-architecture-decision-log.md#adr-0022)),
implemented as **CG1a**. Microsoft Kiota emits a fluent path-builder
SDK that mirrors the URL — power users who learn `/v2/collections/{appId}/data-objects/{appId}`
read the SDK the same way:

```python
do = await sh.collections.by_collection_id(cid) \
                .data_objects.by_data_object_id(oid).get()
```

| Language | Package | Build path |
|---|---|---|
| Python | `shepard-v2-client` (PyPI) | `clients-v2/python-kiota/` |
| TypeScript | `@dlr-shepard/v2-client` (npm) | `clients-v2/typescript-kiota/` |
| Java | `de.dlr.shepard:shepard-v2-client-java` (Maven Central) | `clients-v2/java-kiota/` |

Publishing to the public registries is **CG1c** — pending. Until it
ships, build locally from `clients-v2/`. See the per-language READMEs
in that directory for quick-starts.

### 2. Calling `/shepard/api/` endpoints — use the OpenAPI Generator clients at `clients/`

This is the **legacy still-maintained** path ([ADR-0022](https://github.com/noheton/shepard/blob/main/aidocs/63-architecture-decision-log.md#adr-0022),
**CG1b**). The upstream `dlr-shepard-clients/*` packages are byte-compatible
with the upstream shepard project — anything you've built against them
keeps working against this fork.

| Language | Package | Build path |
|---|---|---|
| Python | `shepard-client` (PyPI) | `clients/python/` (upstream pipeline) |
| TypeScript | `@dlr-shepard/client` (npm) | `clients/typescript/` (upstream pipeline) |
| Java | `de.dlr.shepard:shepard-client` (Maven Central) | `clients/java/` |

Neither generator is on a deprecation path — both live indefinitely.
You can use both in the same project; the distribution coordinates
are distinct so the two packages co-exist in a single `venv` /
`node_modules` / Maven classpath without collision.

### 3. Casual use case (Python or TypeScript) — use the convenience wrapper

For ergonomic, hand-written wrappers that ride **on top of** the
generated clients — see [`aidocs/27`](https://github.com/noheton/shepard/blob/main/aidocs/27-convenience-clients-design.md)
(`shepard-py` / `shepard-ts`). Generator-agnostic; the wrapper hides
the four-surface complexity behind a single import:

```python
from shepard import Shepard
sh = Shepard("https://shepard.example.com", api_key=KEY)
for collection in sh.collections.list():
    ...
```

The wrapper retarget to ride on top of the Kiota-generated `/v2/`
client (instead of the OpenAPI-Generator one) is tracked as a separate
slice — not yet shipped.

## Decision tree

```text
You want to call shepard from code. Which client?

├── Did you build against upstream shepard's clients already?
│   └── Yes → STAY on `clients/` (`/shepard/api/`) — no migration cost,
│             your code keeps working byte-for-byte.
│
├── Calling endpoints under `/v2/...` (new in this fork)?
│   ├── Are you doing scripting / one-shot tasks?
│   │   └── Use the **convenience wrapper** (`shepard-py` / `shepard-ts`).
│   └── Building a long-lived integration?
│       └── Use the **Kiota client** at `clients-v2/` — fluent
│            path-builder, native OpenAPI 3.1, MCP-extension-friendly.
│
└── Calling endpoints under `/shepard/api/...` (upstream surface)?
    └── Use the **OpenAPI Generator client** at `clients/`.
```

## Cross-references

- [`aidocs/57 §4`](https://github.com/noheton/shepard/blob/main/aidocs/57-openapi-client-generator-evaluation.md#4-recommendation) — full generator evaluation.
- [`aidocs/63` ADR-0022](https://github.com/noheton/shepard/blob/main/aidocs/63-architecture-decision-log.md#adr-0022) — dual-generator decision record.
- [`aidocs/27`](https://github.com/noheton/shepard/blob/main/aidocs/27-convenience-clients-design.md) — convenience-wrapper design.
- [`aidocs/16` CG1](https://github.com/noheton/shepard/blob/main/aidocs/16-dispatcher-backlog.md) — dispatcher slot tracking CG1a / CG1b / CG1c / CG1d.
- `clients-v2/README.md` — layout and regeneration steps for the Kiota tree.
