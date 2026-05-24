---
stage: deployed
last-stage-change: 2026-05-24
---

# OPS hygiene bundle — 2026-05-24

Three small operational fixes shipped as one PR. All three originated as
side-findings from today's parallel agent runs. Branch:
`worktree-agent-a2adf9cb43b440444`.

Closes:
- `CRIT-SMOKE-NOT-CATCHING-BUILD-BREAK`
- `CRIT-WORKTREE-DOCKER-CACHE`
- `MK-REDEPLOY-FORCE-RECREATE`
- `E2E-AUTH-TOLERANT-LOGIN`

Surfaces (new row, filed in this PR):
- `CRIT-MAIN-BROKEN-TARGETENTITYRESOLVER-DUPLICATE`

## What I found

Three traps that all share one shape: **the safety net was probing the
running state, not the source of truth**.

1. `make smoke` was probing a live container. A broken `mvn clean compile`
   on `main` never failed the smoke gate, because the smoke gate didn't
   compile anything — it just hit endpoints on a stale image that had
   been built and deployed before the break landed.
2. `make redeploy-*` rebuilt the image but didn't recreate the container.
   `docker compose up -d --no-build <svc>` is a no-op when the container
   is already running with the same image *tag* — even though the image
   hash under that tag has changed. The container kept serving the old
   code; smoke + `curl /` both returned 200 against stale-but-running.
3. `e2e/tests/helpers/auth.ts:loginAs` assumed every login walked the
   Keycloak credentials form. When the Keycloak SSO cookie was still
   hot, the Sign In button bounced straight back to `/` via the OIDC
   callback (no form shown), and `waitForURL(KC)` timed out. Three
   independent specs ended up pasting in their own local `tolerantLogin`
   override; a fourth (`rdm-002`) shadow-declared a local non-tolerant
   `loginAs` and didn't even import the helper.

## Opportunities

The bundle is the opportunity. Three fixes, no new code outside ops files.

## Ideas

### Fix 1: `make smoke` depends on `make build-backend`

**Before** (`Makefile:94`):

```make
smoke:
	@$(COMPOSE_DIR)/smoke-test.sh
```

**After** (`Makefile:103`):

```make
smoke: build-backend
	@$(COMPOSE_DIR)/smoke-test.sh
```

Plus a doc-block explaining the trade-off: smoking PROD with
`FRONTEND_URL=https://... make smoke` now also runs a local Maven build.
The documented escape hatch is direct script invocation:
`FRONTEND_URL=... ./infrastructure/smoke-test.sh`.

`smoke` is also called recursively from `redeploy*` targets via
`@$(MAKE) smoke`. The recursive sub-make does not dedup `build-backend`
across the redeploy chain → ~5-15s of redundant `-Dmaven.test.skip=true`
re-pass. Accepted as a wart, not engineered around (gold-plating).

### Fix 2: `--force-recreate <service>` on every redeploy

**Before** (`Makefile`):

```make
redeploy-backend: preflight-env image-backend
	cd $(COMPOSE_DIR) && docker compose up -d --no-build backend
	…
redeploy-frontend: preflight-env image-frontend
	cd $(COMPOSE_DIR) && docker compose up -d --no-build frontend
	…
redeploy: preflight-env image-backend image-frontend
	cd $(COMPOSE_DIR) && docker compose up -d --no-build backend frontend
	…
redeploy-fast: preflight-env image-backend image-frontend
	cd $(COMPOSE_DIR) && docker compose up -d --no-build backend frontend
```

**After**:

```make
redeploy-backend: preflight-env image-backend
	cd $(COMPOSE_DIR) && docker compose up -d --no-build --force-recreate backend
	…
redeploy-frontend: preflight-env image-frontend
	cd $(COMPOSE_DIR) && docker compose up -d --no-build --force-recreate frontend
	…
redeploy: preflight-env image-backend image-frontend
	cd $(COMPOSE_DIR) && docker compose up -d --no-build --force-recreate backend frontend
	…
redeploy-fast: preflight-env image-backend image-frontend
	cd $(COMPOSE_DIR) && docker compose up -d --no-build --force-recreate backend frontend
```

`verify-frontend-deploy` (sentinel-check via `docker exec ... grep`)
deliberately deferred per the OPS-bundle task brief: `--force-recreate`
alone closes 80% of the trap; the verify target is a follow-up if the
remaining 20% bites.

### Fix 3: shared `loginAs` becomes tolerant; local copies deleted

`e2e/tests/helpers/auth.ts:loginAs` now handles both paths:

- **Cookie-cold** (case 1): Sign In button → Keycloak credentials form
  → fill + submit → redirect back to app.
- **Cookie-hot** (case 2): Sign In button bounces straight back to `/`
  via OIDC callback; no form is ever shown. The previous shape's
  `waitForURL(KC)` timed out here ~30% of the time.

Pattern lifted from `e2e/tests/ux-polish-2026-05-24.spec.ts` (cleanest
race-based shape) plus the early "already signed in" short-circuit from
`rdm-001`'s local override (`goto /` first, check `SIGN OUT` visible,
return immediately if so — cheapest path on hot SSO).

Local overrides removed (now redundant):

| File | What was deleted |
|---|---|
| `e2e/tests/ux-polish-2026-05-24.spec.ts` | `tolerantLogin` function (39 LOC) |
| `e2e/tests/rdm-001-cite-this-dataset.spec.ts` | `loginAsTolerant` function (39 LOC) |
| `e2e/tests/rdm-005-metadata-completeness.spec.ts` | `loginAsTolerant` function (31 LOC) |
| `e2e/tests/ux-pattern-d-count-badges.spec.ts` | `loginAsTolerant` function (31 LOC) |
| `e2e/tests/rdm-002-orcid-on-profile.spec.ts` | Local non-tolerant `loginAs` (19 LOC) — shadow-declared the import name and DIDN'T import the helper at all; was a stale paste that pre-dated the helper. |

All five specs now call the shared `loginAs(page, username, password)`.
Net delta: ~159 LOC removed from specs, ~80 LOC added to the helper.

## Real-world impact

### The `make smoke` self-test — what was supposed to happen

I planted a syntax-error probe file
(`backend/src/main/java/de/dlr/shepard/SmokeBreakProbe.java`) containing
the single line:

```java
package de.dlr.shepard; SYNTAX_ERROR_FOR_SMOKE_SELFTEST;
```

Ran `make smoke`. Expected: non-zero exit. Got:

```text
$ make smoke > /tmp/smoke-broken.log 2>&1; echo "MAKE_EXIT=$?"
MAKE_EXIT=2

[ERROR] COMPILATION ERROR :
[ERROR] .../de/dlr/shepard/SmokeBreakProbe.java:[1,25] class, interface, enum, or record expected
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin:3.15.0:compile (default-compile) on project backend: Compilation failure
make: *** [Makefile:39: build-plugins] Error 1
```

Removed the probe (`rm backend/src/main/java/de/dlr/shepard/SmokeBreakProbe.java`).
Reran `make smoke`. **Expected**: green. **Got**: also red, but for a
completely different reason — see next section.

### The `make smoke` self-test — what actually happened (the real catch)

After restoring clean state, `make smoke` still failed with `MAKE_EXIT=2`.
This was NOT my probe leaking — `find . -name SmokeBreakProbe.java` is
empty; my worktree's `git status` shows zero backend changes. The errors
trace back to a real, pre-existing main-branch compile break:

```text
[ERROR] .../provenance/filters/TargetEntityResolver.java:[85,37] method resolve(java.lang.String) is already defined in class de.dlr.shepard.provenance.filters.TargetEntityResolver
[ERROR] .../common/neo4j/daos/GenericDAO.java:[151,53] cannot find symbol
[ERROR] .../common/util/CypherQueryHelper.java:[48,87] cannot find symbol
[ERROR] .../auth/apikey/entities/ApiKey.java:[30,8] de.dlr.shepard.auth.apikey.entities.ApiKey is not abstract and does not override abstract method setAppId(java.lang.String) in de.dlr.shepard.common.identifier.HasAppId
…
```

The headline finding: `TargetEntityResolver.java` declares
**two `resolve(String path)` methods** (one instance at line 49, one
`static` at line 85). The cascade of "cannot find symbol" errors below
follows from that first failure (Lombok APT not running once compile
aborts → all `@Getter`/`@Setter` accessors look missing).

`git log -1 --oneline HEAD` → `373543df backlog: 4 new rows from today's
agent side-findings + RDM-005a` (i.e. the worktree's HEAD is `main`).
Reproduced from canonical `/opt/shepard/backend` as well — same error.
This is a real main-branch break, not local pollution.

Filed as `CRIT-MAIN-BROKEN-TARGETENTITYRESOLVER-DUPLICATE` in
`aidocs/16-dispatcher-backlog.md` (queued).

**The smoke fix worked exactly as designed**: a green smoke now genuinely
means the source compiles. Before the fix, this `TargetEntityResolver`
duplicate had been hiding behind a green smoke gate against a stale
backend container image — running cleanly because the image was built
before the duplicate-method PR landed.

### Backlog flips

| ID | from | to |
|---|---|---|
| `CRIT-SMOKE-NOT-CATCHING-BUILD-BREAK` | queued | ✅ shipped 2026-05-24 |
| `CRIT-WORKTREE-DOCKER-CACHE` | queued | ✅ shipped 2026-05-24 (resolved by `--force-recreate` sibling fix) |
| `MK-REDEPLOY-FORCE-RECREATE` | queued | ✅ shipped 2026-05-24 |
| `E2E-AUTH-TOLERANT-LOGIN` | queued | ✅ shipped 2026-05-24 |
| `CRIT-MAIN-BROKEN-TARGETENTITYRESOLVER-DUPLICATE` | (new) | queued — XS |

## Gaps & blockers

- **`make smoke` is currently red on main** because of
  `CRIT-MAIN-BROKEN-TARGETENTITYRESOLVER-DUPLICATE`. The OPS bundle is
  not the cause — it's the *messenger*. Someone needs to land a fix to
  `TargetEntityResolver.java` (collapse the two overloads or rename
  the static one) before `make smoke` will return green from a fresh
  worktree. Out of scope for this PR per the bundle's hard-rule
  "No backend Java changes".
- **`verify-frontend-deploy` is deferred.** If the docker-cache trap
  recurs despite `--force-recreate`, the sentinel-check target
  (`docker exec infrastructure-frontend-1 grep -l <build-sentinel> /app/server/chunks/build/*.mjs`)
  is the follow-up. Skipped per the task brief.

## What surprised me

The fix immediately caught a real, pre-existing critical break on
`main` that had been silently passing smoke. This is the strongest
possible validation of the fix — and a cleaner self-test than the
synthetic probe I planted. The advisor framing landed it well:
"the fact that the clean restore also fails is a separate finding; it's
the headline, not a problem".

The Lombok-flavored cascade of "cannot find symbol" errors looks like a
recurrence of the `CRIT-LOMBOK-APT` trap that was retracted earlier
today. It's not the same root cause — `CRIT-LOMBOK-APT` was dirty WD;
this is a real duplicate-method on clean HEAD. But the symptom shape is
identical, which is why the retraction-then-recurrence is worth
flagging: any future "Lombok seems broken" report should first check
for upstream compile errors that abort the APT phase.

The `rdm-002` shadow-declaration pattern (importing nothing, declaring
a function with the same name `loginAs`) is a quiet anti-pattern that
the helper-replacement sweep made visible — a code-search for
`loginAs` would have hit five "users" but only four importers. Worth
a follow-up lint rule: `eslint no-redeclare` / `@typescript-eslint/no-shadow`
on file-scope.
