# Garage activation runbook — findings & gotchas

**Runbook:** `docs/ops/garage-activation-runbook.md`
**Audience:** task #122 reviewer + the next agent that touches FS1b/c.
**Date:** 2026-05-22.

## What the runbook delivers

A seven-section, copy-paste-ready guide that takes a nuclide.systems operator
from "backend is up but `/v2/file-containers/{x}/upload-url` returns 503 gridfs"
to "Garage v1.0.1 is up, the s3 adapter is the active provider, presigned upload
URLs work for the v15 MFFD import."

Sections, in order: pre-conditions → compose snippet (with `garage.toml`) →
bucket/key bootstrap → backend env → restart + verify (with two probes) →
rollback → known traps.

Probe 5b in §5 is the load-bearing test: it creates a FileContainer with
`providerId=s3` and asks for an upload URL using the literal Flo Researcher JWT
from `project_mffd_api_keys.md`. 200 = activated; 503 = still on gridfs.

## What I corrected from the task spec — fact-checking caught five errors

The task brief described the runbook in seven sections; I verified each against
the actual code (`plugins/file-s3/src/main/java/.../S3FileStorage.java`,
`backend/.../FileStorageRegistry.java`) and upstream Garage docs
(garagehq.deuxfleurs.fr). Five concrete corrections vs. the brief:

1. **Healthcheck port.** Brief said `http://localhost:3900/health`. Garage v1.0+
   serves `/health` on the **admin port 3903**, not the S3 API port. Container
   would have come up permanently unhealthy on 3900. Verified via the Garage
   admin-api docs.

2. **Path-style env var name.** Brief said `SHEPARD_FILES_S3_PATH_STYLE=true`.
   The actual config key (S3FileStorage.java:102) is
   `shepard.files.s3.path-style-access`, which MicroProfile maps to
   `SHEPARD_FILES_S3_PATH_STYLE_ACCESS` — five underscores, not four. Setting
   the brief's name silently does nothing (the plugin default is `true`, so
   activation would have accidentally worked — luck, not design).

3. **CLI subcommand.** Brief used `garage key new`. Garage v1.0+ docs use
   `garage key create`. Both may alias, but the docs example uses `create` —
   I went with the documented form.

4. **Key output field names.** Brief said the operator captures
   `access_key_id` and `secret_access_key` from the CLI. Garage actually prints
   `Key ID:` and `Secret key:` (verbatim, with those labels). The runbook shows
   the literal output shape so operators don't pattern-fail.

5. **`bucket allow` flags.** Brief had `--read --write`. Garage's documented
   form includes `--owner` as well, which is what you want for the backend's
   bucket (the backend creates objects and needs ownership for ACL ops). Added
   `--owner`.

Also added `garage.toml` as a separate file (the brief didn't mention config
file vs env). Garage v1.0+ won't start without a config file and there's no
"all-env" mode comparable to e.g. minio.

## Gotchas an operator will actually hit

Captured as §7 "Known traps." The non-obvious ones:

- **Layout-before-bucket.** Garage v1.0+ refuses bucket ops until
  `layout apply --version 1` runs. Pre-v1.0 was looser; the error is
  `Cluster layout not yet initialized`.
- **Backend env reload requires `--force-recreate`.** A plain `docker compose
  restart backend` doesn't necessarily re-source the env file in all setups.
  `up -d --force-recreate` is unambiguous.
- **`SHEPARD_STORAGE_PROVIDER` is deploy-time only.** The CLAUDE.md
  "admin-configurable" cluster-identity exception applies; there is no
  PATCH endpoint for this. `FileStorageRegistry.resolve()` runs on
  `StartupEvent` (line 116) and caches for the JVM lifetime.
- **RPC secret stability.** Garage derives node identity from `rpc_secret`;
  regenerating it after `layout assign` orphans the cluster. Pin it once.
- **Secrets shown once.** `garage key create` displays the secret a single
  time. Capture immediately or re-mint.
- **`/v2/admin/storage` requires `instance-admin`.** The Flo Researcher JWT
  in `project_mffd_api_keys.md` lacks that role; probe 1 (the storage adapter
  list) is optional. Probe 2 (the upload-url probe) is the load-bearing one
  and works with researcher-role tokens.

## What the operator should see post-activation

1. `docker compose up -d garage` — container `shepard-garage` in `Up (healthy)`.
2. `docker exec shepard-garage /garage status` — one node, status `HEALTHY`.
3. `docker exec shepard-garage /garage bucket info shepard-files` — bucket
   exists, authorized keys list shows `shepard-backend (read, write, owner)`.
4. Backend logs: `S3FileStorage` initialization log line (S3FileStorage.java
   `@PostConstruct` emits a log) and `FileStorageRegistry: activeStorage=s3`.
5. Probe 5b — `HTTP 200` with body
   `{"uploadUrl":"http://garage:3900/shepard-files/...","oid":"<uuid>","expiresAt":"..."}`
6. Optional full round-trip: a literal `PUT` against the `uploadUrl` returns
   200 from Garage; the bytes never touch the backend.

## Followups not in this runbook

- `aidocs/integrations/93 §9` plugin-declared sidecars — when
  `FileS3PluginManifest.sidecars()` ships, this runbook becomes
  "machine-generated, you don't run it by hand." Until then it stays as the
  manual path.
- The mffd v15 import pre-flight (§9 of `aidocs/integrations/93`) detects
  the 503 gridfs state and prints a pointer to this runbook. That linkage
  isn't wired yet — when v15 ships its pre-flight, point the link at
  `docs/ops/garage-activation-runbook.md`.
- `aidocs/34-upstream-upgrade-path.md` should get an FS1e row referencing
  this runbook as the "how to activate" companion to FS1b/c. (The runbook
  itself isn't a code change; it's an ops asset. The 34 tracker entry would
  be at activation time, not at runbook landing.)

## What I did NOT do

- Did NOT deploy Garage to nuclide.systems — the brief said read-only on the
  codebase and "do not deploy." This is the operator's runbook.
- Did NOT call any mutating endpoint on nuclide.systems. The 503 from the
  upload-url probe was already confirmed in `aidocs/integrations/93 §9`;
  re-confirming wasn't worth a state-change.
- Did NOT verify the Flo Researcher JWT is still valid — my read-only probe
  returned 401 (likely expired since the 2026-05-22 issuance). The runbook
  notes this and tells the operator to re-mint if 401 appears. Per
  `feedback_no_redactions.md` I still pasted the literal JWT.
