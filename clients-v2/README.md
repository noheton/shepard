# `clients-v2/` — Kiota-generated `/v2/` API clients

This tree holds the **CG1a** Kiota baseline for shepard's `/v2/` API
surface. Per
[ADR-0022](../aidocs/63-architecture-decision-log.md#adr-0022)
([discussion in `aidocs/57 §4.1`](../aidocs/57-openapi-client-generator-evaluation.md)),
[Microsoft Kiota](https://learn.microsoft.com/openapi/kiota/overview)
is the new baseline generator for `/v2/` clients.

## Relationship to `clients/`

| Surface | Lives at | Generator | Slot |
|---|---|---|---|
| `/shepard/api/...` (byte-frozen upstream-compat) | `../clients/` | OpenAPI Generator | CG1b — still-maintained legacy |
| `/v2/...` (this fork's development surface) | `../clients-v2/` (here) | **Microsoft Kiota** | CG1a — new baseline |

Both generators stay maintained indefinitely (`ADR-0022`). Neither is on
a deprecation path. An operator picks whichever fits their tooling.

The two trees' artefacts use distinct distribution coordinates so a
consumer can pull both into the same project:

| Language | `/shepard/api/` (legacy) | `/v2/` (this tree) |
|---|---|---|
| Python | `shepard-client` (PyPI) | `shepard-v2-client` (PyPI) |
| TypeScript | `@dlr-shepard/client` (npm) | `@dlr-shepard/v2-client` (npm) |
| Java | `de.dlr.shepard:shepard-client` | `de.dlr.shepard:shepard-v2-client-java` |

## Layout

```
clients-v2/
├── Makefile                  # `make generate-all` etc.
├── README.md                 # this file
├── python-kiota/
│   ├── pyproject.toml        # shepard-v2-client distribution
│   ├── README.md
│   └── shepard_v2/           # GENERATED — `make generate-python`
├── typescript-kiota/
│   ├── package.json          # @dlr-shepard/v2-client distribution
│   ├── tsconfig.json
│   ├── README.md
│   └── src/                  # GENERATED — `make generate-typescript`
├── java-kiota/
│   ├── pom.xml               # de.dlr.shepard:shepard-v2-client-java
│   ├── README.md
│   └── src/main/java/de/dlr/shepard/v2/clients/   # GENERATED — `make generate-java`
└── tests/smoke/              # per-language smoke tests (CI verification)
```

The generated trees are **vendored** into the repo (per `aidocs/57 §8 q3` —
maintainer-confirmed in ADR-0022). PR diffs surface the exact API change
shape; the CI workflow at `.github/workflows/clients-kiota.yml`
re-generates on every release tag to confirm reproducibility.

## Regenerating locally

```bash
# 1. Install Kiota (pinned version — see Makefile KIOTA_VERSION)
dotnet tool install --global Microsoft.OpenApi.Kiota --version 1.31.1

# 2. Boot the backend so /shepard/doc/openapi/v2.json is reachable
cd ../infrastructure && docker compose up -d backend && cd -

# 3. Generate all three language clients
make generate-all

# Or one at a time:
make generate-python
make generate-typescript
make generate-java
```

Inputs are configurable via env vars (see `make help`):

- `KIOTA_INPUT_URL` — defaults to `http://localhost:8080/shepard/doc/openapi/v2.json`;
  override to a tagged-release URL for release-time generation.
- `KIOTA_BIN` — defaults to `kiota` on PATH.

## Consuming the generated clients

See the per-language READMEs:

- `python-kiota/README.md` — Python quick-start
- `typescript-kiota/README.md` — TypeScript quick-start
- `java-kiota/README.md` — Java quick-start

## On top: the convenience wrapper (`aidocs/27`)

The hand-written `shepard-py` / `shepard-ts` ergonomic wrappers
described in [`aidocs/27`](../aidocs/27-convenience-clients-design.md)
sit **on top of** these Kiota-generated clients. The wrapper retarget
to consume Kiota's import surface is tracked as a separate task — for
now the wrapper still targets the OpenAPI-Generator output. See
[`aidocs/27 §"Generator-agnostic"`](../aidocs/27-convenience-clients-design.md).

## Out of scope here

- Publishing to PyPI / npm / Maven Central — **CG1c**.
- OpenAPI Generator side of the dual baseline — **CG1b** (sibling PR).
- Go / Rust / C# language additions — **CG1d**.
- Hey API TanStack-Query frontend client — **CG1e** (deferred).
- `x-mcp-*` extension surfacing in generated metadata — **CG1f** (deferred).
