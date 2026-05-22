# Garage S3 sidecar — activation runbook for `shepard-api.nuclide.systems`

**Audience:** the operator (fkrebs@nucli.de) standing on the nuclide.systems host.
**Goal:** bring up a Garage v1.0.1 container as the S3 backend for the `shepard-plugin-file-s3` adapter (FS1b/c) so the MFFD v15 import can use presigned-URL uploads.
**Status of the code path:** plugin shipped (FS1b/c/d, see `aidocs/34-upstream-upgrade-path.md`). Garage container itself NOT yet deployed; live probe confirms `POST /v2/file-containers/{x}/upload-url` returns `503 "Active storage provider 'gridfs' does not support presigned upload URLs"`.
**Bootstrap shape:** this is the **manual compose-override path**, valid until the plugin-declared sidecar mechanism (per `aidocs/integrations/93-mffd-import-v15-requirements.md §9`) lands. Once `FileS3PluginManifest.sidecars()` exists, the compose snippet below becomes machine-generated. For now an operator pastes it by hand.

---

## 1. Pre-conditions

Run these before touching anything:

```bash
# Backend is up?
curl -fsS https://shepard-api.nuclide.systems/versionz && echo "OK"
# Expected: a version JSON, no error. If 404 the backend isn't healthy yet — fix that first.

# You can reach the host that runs the backend (SSH + docker)?
ssh nuclide.host "docker ps --filter name=shepard --format '{{.Names}}\t{{.Status}}'"
# Expected: a 'shepard-backend' (or similar) container in Up state.

# You know where the compose file lives?
ssh nuclide.host "find / -name 'docker-compose*.yml' -not -path '*/node_modules/*' 2>/dev/null | head"
# Note the path of the main shepard compose stack. Subsequent commands assume cwd = that directory.
```

If any of those fails, do not proceed — fix orientation first, then return.

---

## 2. Garage container — compose override

On the host, in the directory of the main shepard compose stack, create or extend
`docker-compose.override.yml`:

```yaml
services:
  garage:
    image: dxflrs/garage:v1.0.1
    container_name: shepard-garage
    restart: unless-stopped
    networks:
      - shepard
    ports:
      # NOTE: do NOT publish 3900 / 3902 / 3903 to the host's public interface
      # unless you front them with a reverse proxy + auth. The backend reaches
      # Garage via the docker network; nothing else should.
      - "127.0.0.1:3900:3900"   # S3 API (loopback only)
      - "127.0.0.1:3902:3902"   # web (loopback only)
      - "127.0.0.1:3903:3903"   # admin / metrics / /health (loopback only)
    volumes:
      - garage_data:/var/lib/garage/data
      - garage_meta:/var/lib/garage/meta
      - ./garage.toml:/etc/garage.toml:ro
    healthcheck:
      # Garage v1.0+ serves /health on the ADMIN port (3903), not the S3 API
      # port. Spec'ing 3900 here results in a permanently-unhealthy container.
      test: ["CMD", "wget", "-q", "-O", "-", "http://localhost:3903/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 30s

volumes:
  garage_data:
  garage_meta:

networks:
  shepard:
    external: true   # join the existing backend network; rename if yours differs
```

Then, in the same directory, write `garage.toml` (Garage's config file):

```toml
metadata_dir = "/var/lib/garage/meta"
data_dir = "/var/lib/garage/data"

db_engine = "lmdb"

replication_factor = 1

# RPC secret: generated once with `openssl rand -hex 32`. Pin it here so the
# node identity is stable across restarts. Replace the placeholder before
# starting the container — and do NOT regenerate this value after layout
# assign; you'd orphan the cluster.
rpc_secret = "REPLACE_WITH_OUTPUT_OF_openssl_rand_-hex_32"
rpc_bind_addr = "[::]:3901"
rpc_public_addr = "127.0.0.1:3901"

[s3_api]
s3_region = "garage-region"
api_bind_addr = "[::]:3900"
root_domain = ".s3.garage.local"

[s3_web]
bind_addr = "[::]:3902"
root_domain = ".web.garage.local"
index = "index.html"

[admin]
api_bind_addr = "[::]:3903"
# admin_token is OPTIONAL; the /health endpoint is unauthenticated by design.
# If you set admin_token, the operator commands later still work because we
# use `docker exec` (which talks to the local socket, not the HTTP API).
```

Generate the RPC secret and substitute it in:

```bash
SECRET=$(openssl rand -hex 32)
sed -i "s|REPLACE_WITH_OUTPUT_OF_openssl_rand_-hex_32|$SECRET|" garage.toml
grep rpc_secret garage.toml   # confirm it's no longer the placeholder
```

Bring Garage up:

```bash
docker compose up -d garage
docker compose logs --tail=50 garage
```

You should see Garage's startup banner ending with something like
`Garage daemon is now ready`. If it complains about the RPC secret, you forgot
to replace the placeholder.

---

## 3. Bucket + key bootstrap (post-up)

These run **after** `docker compose up -d garage` and **before** restarting the
backend.

```bash
# Confirm the node is up and grab its node-id
docker exec shepard-garage /garage status
```

Expected shape — a table with one node, status `HEALTHY`, with a long hex
node-id in the first column. **Copy the node-id** (the full hex string before
the `@hostname:port` part).

```bash
# Assign the only node to one zone with 1 GB capacity (one-node dev cluster)
# Garage v1.0+ syntax: -z <zone> -c <capacity> <node-id>
NODE_ID=<paste-the-node-id-from-status>
docker exec shepard-garage /garage layout assign -z dc1 -c 1G $NODE_ID

# Stage the layout and apply version 1
docker exec shepard-garage /garage layout show
docker exec shepard-garage /garage layout apply --version 1

# Create the bucket the backend will write to
docker exec shepard-garage /garage bucket create shepard-files

# Mint the backend's access key. NOTE: the secret is shown ONCE at creation
# time — capture it now or you will be re-rolling the key.
docker exec shepard-garage /garage key create shepard-backend
```

The `key create` output looks like:

```
Key name: shepard-backend
Key ID: GK3515373e4c851ebaad366558
Secret key: 7d37d093435a41f2aab8f13c19ba067d9776c90215f56614adad6ece597dbb34
Authorized buckets:
```

**Copy `Key ID` and `Secret key` immediately into a secure note** — you'll
paste them into the backend env in §4.

Grant the new key read+write+owner on the bucket:

```bash
docker exec shepard-garage /garage bucket allow \
  --read --write --owner \
  shepard-files \
  --key shepard-backend
```

Sanity check:

```bash
docker exec shepard-garage /garage bucket info shepard-files
# Expected: "Authorized keys: shepard-backend (read, write, owner)"

curl -fsS http://localhost:3903/health
# Expected: 200 OK ("Garage is fully operational" or similar). 503 means the
# layout didn't apply — re-check the layout apply step above.
```

---

## 4. Backend env config

The backend uses Quarkus's standard `name.with.dots` → `NAME_WITH_DOTS_OR_UNDERSCORES`
env mapping. The keys come from `S3FileStorage.java` lines 87–102.

Add to the backend service's env block in compose (or to its env-file):

```bash
# Active-adapter selector. Deploy-time only — the backend reads this at
# @PostConstruct and never re-reads it. A restart is required for the flip
# to take effect (see §5). This is by design: switching the storage backend
# at runtime would orphan in-flight writes (CLAUDE.md "admin-configurable"
# cluster-identity exception).
SHEPARD_STORAGE_PROVIDER=s3

# S3 endpoint — the in-network hostname is 'garage' (the compose service name).
SHEPARD_FILES_S3_ENDPOINT=http://garage:3900
SHEPARD_FILES_S3_REGION=garage-region
SHEPARD_FILES_S3_BUCKET=shepard-files

# Path-style addressing. Non-negotiable for Garage (it does NOT support
# virtual-host buckets the way AWS does). NOTE the env-var name: the config
# key is shepard.files.s3.PATH-STYLE-ACCESS (not just 'path-style'); the
# env-var form is SHEPARD_FILES_S3_PATH_STYLE_ACCESS. The plugin defaults
# this to true so a missing value won't break Garage, but set it explicitly
# so the next operator who skims the env doesn't guess.
SHEPARD_FILES_S3_PATH_STYLE_ACCESS=true

# Credentials from §3 (literal copy of the `garage key create` output).
SHEPARD_FILES_S3_ACCESS_KEY_ID=GK3515373e4c851ebaad366558
SHEPARD_FILES_S3_SECRET_ACCESS_KEY=7d37d093435a41f2aab8f13c19ba067d9776c90215f56614adad6ece597dbb34
```

Substitute the literal `Key ID` and `Secret key` you copied — the values
above are the upstream-docs example placeholders, NOT real credentials.

---

## 5. Restart + verify

Force-recreate so the new env actually takes effect (a plain restart will not
re-read deploy-time-only config in all setups):

```bash
docker compose up -d --force-recreate backend
```

Wait for healthy:

```bash
until curl -fsS https://shepard-api.nuclide.systems/versionz >/dev/null; do
  echo "waiting..."; sleep 5
done
echo "backend up"
```

**Probe 1 — storage adapter list (admin-only).** This uses the
`instance-admin`-roled token; the Flo Researcher key from
`/root/.claude/projects/-opt-shepard/memory/project_mffd_api_keys.md`
does not have instance-admin. If you have an admin token, swap it in here;
if not, skip this probe and rely on probe 2 (which is the load-bearing one).

```bash
curl -fsS \
  -H "Authorization: Bearer <YOUR_INSTANCE_ADMIN_TOKEN>" \
  https://shepard-api.nuclide.systems/v2/admin/storage | jq
# Expected: { "activeId": "s3", "adapters": [..., {"id":"s3","enabled":true,"active":true}, ...] }
```

**Probe 2 — the critical sanity check.** Create a FileContainer with
`providerId=s3`, then ask for an upload URL. This is the exact flow the v15
importer uses; it must return 200 with `{uploadUrl, oid, expiresAt}`, NOT 503.

Note: the JWT below is the Flo Researcher key from
`project_mffd_api_keys.md` (issued 2026-05-22). If it returns 401, the
session JWT has expired — re-mint it via your standard issuance flow
(`/q/login` against the OIDC issuer, or the operator-side mint script)
and substitute the new value.

```bash
# 5a — create a FileContainer with providerId=s3 (returns appId)
CONT_APPID=$(curl -fsS -X POST \
  -H "Authorization: Bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJlZTRjMDEwZi1kNjQ4LTQ2MzAtYWVhNi1iODFlZjJhOWMyOTYiLCJpc3MiOiJodHRwOi8vc2hlcGFyZC1hcGkubnVjbGlkZS5zeXN0ZW1zLyIsIm5iZiI6MTc3OTQzMTY1MiwiaWF0IjoxNzc5NDMxNjUyLCJqdGkiOiI3NmMyMGM2MC1hMzVmLTQwMjQtODdhNi0xYjU0ZGRiMzcxZWIifQ.ZBY9YQZyje_ketIGB2za50H76XR-oYmCWy6wHdySBX3o2mhWgGCASrjjkmIyRDwlmQfM4MR-BtTUzS7Vp1XTROERu3AbiF-y-7CWmxHWvP0NVJ1Cl_EjdcXJjztnU8rjb-jTY5t1WOQeSgBszMDq8cwNY-67w4Xj5tyvQRq7i928kIHFiepfKg6mCHo6JVHMIdJyUHKri9J1GmbopdM7pdpN074BYxYzZQ8qCgMDN2MrMq37HjDwFrhhu1y7BPDJuglCXdM0jtU--L5aSZyENcMCiwZQyPf6Bf3AX7ddY2EDsNtB7xFgeJ7XtHVTs4yItHZmm0TTdb1-Q7lFQ19-Cg" \
  -H "Content-Type: application/json" \
  -d '{"name":"garage-activation-probe","providerId":"s3"}' \
  https://shepard-api.nuclide.systems/v2/file-containers | jq -r '.appId')
echo "container appId: $CONT_APPID"

# 5b — ask for a presigned upload URL on that container
curl -i -X POST \
  -H "Authorization: Bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJlZTRjMDEwZi1kNjQ4LTQ2MzAtYWVhNi1iODFlZjJhOWMyOTYiLCJpc3MiOiJodHRwOi8vc2hlcGFyZC1hcGkubnVjbGlkZS5zeXN0ZW1zLyIsIm5iZiI6MTc3OTQzMTY1MiwiaWF0IjoxNzc5NDMxNjUyLCJqdGkiOiI3NmMyMGM2MC1hMzVmLTQwMjQtODdhNi0xYjU0ZGRiMzcxZWIifQ.ZBY9YQZyje_ketIGB2za50H76XR-oYmCWy6wHdySBX3o2mhWgGCASrjjkmIyRDwlmQfM4MR-BtTUzS7Vp1XTROERu3AbiF-y-7CWmxHWvP0NVJ1Cl_EjdcXJjztnU8rjb-jTY5t1WOQeSgBszMDq8cwNY-67w4Xj5tyvQRq7i928kIHFiepfKg6mCHo6JVHMIdJyUHKri9J1GmbopdM7pdpN074BYxYzZQ8qCgMDN2MrMq37HjDwFrhhu1y7BPDJuglCXdM0jtU--L5aSZyENcMCiwZQyPf6Bf3AX7ddY2EDsNtB7xFgeJ7XtHVTs4yItHZmm0TTdb1-Q7lFQ19-Cg" \
  -H "Content-Type: application/json" \
  -d '{"fileName":"probe.txt"}' \
  https://shepard-api.nuclide.systems/v2/file-containers/$CONT_APPID/upload-url
```

**Pass:** `HTTP/1.1 200 OK` and a body like
`{"uploadUrl":"http://garage:3900/shepard-files/...?X-Amz-Algorithm=...", "oid":"<uuid>", "expiresAt":"..."}`

**Fail (still on gridfs):** `HTTP/1.1 503` with body containing
`"Active storage provider 'gridfs' does not support presigned upload URLs"` —
this is the same probe used to confirm the pre-activation state. If you see
this after the restart, the env didn't take effect: confirm
`SHEPARD_STORAGE_PROVIDER` is exactly `s3` (no whitespace, no quoting issue)
and that the backend container's runtime env contains the value
(`docker exec shepard-backend env | grep SHEPARD_STORAGE_PROVIDER`).

**Fail (other 5xx):** check `docker compose logs --tail=200 backend` —
the most common case is bad credentials (the backend will log AWS SDK
`AccessDenied` or `SignatureDoesNotMatch`).

Optional but worth doing — **full round-trip:**

```bash
# Read the uploadUrl out of probe 5b's response into $URL, then:
echo "hello from garage" > probe.txt
curl -i -X PUT --data-binary @probe.txt "$URL"
# Expected: 200 OK from Garage (the bytes went direct, the backend did not see them).
```

---

## 6. Rollback

If anything goes wrong and you want to revert to the pre-activation (broken
for v15 imports, but otherwise functional gridfs) state:

```bash
# Stop and remove the Garage container — data volume stays for the next try.
docker compose stop garage
docker compose rm -f garage

# Revert backend env: either flip SHEPARD_STORAGE_PROVIDER back to 'gridfs'
# (explicit) or remove the key (the default in FileStorageRegistry.java line 87
# is 'gridfs'). Remove the SHEPARD_FILES_S3_* entries too — they're inert
# without an s3 provider but cluttering env is bad form.
# Edit docker-compose.yml or the backend env-file accordingly.

# Restart backend
docker compose up -d --force-recreate backend

# Confirm rollback — should be 503 gridfs again on the probe
curl -i -X POST \
  -H "Authorization: Bearer <Flo-Researcher-JWT>" \
  -H "Content-Type: application/json" \
  -d '{"fileName":"probe.txt"}' \
  https://shepard-api.nuclide.systems/v2/file-containers/$CONT_APPID/upload-url
# Expected: 503 with the gridfs message. That's the "back to broken" state — fine,
# you've just reset for the next attempt. The garage_data + garage_meta volumes
# persist; next activation re-uses them or you can `docker volume rm` to start clean.
```

If you want a clean slate including the Garage data:

```bash
docker volume rm $(docker volume ls -q | grep garage)
# Volumes are named after the compose project — usually `<project>_garage_data`
# and `<project>_garage_meta`. Adjust the grep if your project name shares the prefix.
```

---

## 7. Known traps

1. **Garage v1.0+ requires `layout assign` BEFORE `bucket create`.** Pre-v1.0
   was looser; v1.0 refuses bucket operations until layout v1 is applied.
   The error is `Cluster layout not yet initialized`.

2. **`path-style-access=true` is non-negotiable for Garage.** It does not
   support virtual-host buckets the way AWS does. The plugin's default is
   `true` so an omitted env doesn't break activation, but set it explicitly
   for the next operator's sanity.

3. **The env-var name is `SHEPARD_FILES_S3_PATH_STYLE_ACCESS`** — five
   underscores, not four. The MicroProfile mapping is
   `shepard.files.s3.path-style-access` → `SHEPARD_FILES_S3_PATH_STYLE_ACCESS`,
   because hyphens-in-config-key become underscores-in-env, and dots between
   words also become underscores. Calling it `SHEPARD_FILES_S3_PATH_STYLE`
   silently does nothing.

4. **Backend reads `shepard.storage.provider` once at `@PostConstruct`** —
   no runtime flip. `FileStorageRegistry.resolve()` runs on `StartupEvent`;
   the value is cached for the lifetime of the JVM. There is no
   `PATCH /v2/admin/storage` for this knob (and there shouldn't be — flipping
   it at runtime would orphan in-flight writes).

5. **The `garage` service hostname** in the backend's env (`http://garage:3900`)
   is the compose service name, not the container_name. If you renamed the
   service, update the env. From outside the docker network the hostname
   `garage` is not resolvable — backend has to be on the `shepard` network.

6. **`/health` is on the admin port 3903, NOT the S3 API port 3900.** Earlier
   drafts of this runbook used 3900 and the container was permanently
   `unhealthy` — Garage doesn't serve /health on its S3 API port. Use 3903
   (also where /metrics lives).

7. **Secret keys are shown ONCE at creation** by `garage key create`. If you
   lose the secret, mint a new key and revoke the old one. There is no
   `garage key info <name>` that prints the secret again.

8. **The plugin manifest does not declare sidecars yet.** This runbook is
   the manual-compose path; once `FileS3PluginManifest.sidecars()` ships
   (per task #143 + `aidocs/integrations/93 §9`), `scripts/activate-plugin-sidecars.sh`
   (or its analogue) will generate everything in §2–§4 from the manifest
   and this runbook becomes the "what's going on under the hood" reference,
   not the "what you actually run" reference.

9. **RPC secret stability matters.** Garage uses `rpc_secret` to derive node
   identity. Regenerating it after `layout assign` orphans the cluster —
   the node will come up with a new identity and the layout will point at
   the now-missing old id. Pin the secret in `garage.toml` and don't touch
   it. If you must rotate, do it BEFORE assigning the layout.

10. **Loopback port publishing is a soft guardrail, not security.** The
    `127.0.0.1:3900:3900` form in §2 prevents accidentally exposing Garage
    to the public host interface, but anything on the host can still reach
    it. For production, drop the host-side port mappings entirely and rely
    on the docker network — only the backend needs to talk to Garage.
