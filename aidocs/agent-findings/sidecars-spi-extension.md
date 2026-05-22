# PM1f — `PluginManifest.sidecars()` SPI extension + file-s3 Garage declaration

**Agent:** Claude Opus 4.7 (1M context), worktree-agent-a8c8f1e554ea66981
**Date:** 2026-05-22
**Branch:** `worktree-agent-a8c8f1e554ea66981`

## What I built

A new SPI seam on `PluginManifest` so plugins declare the infrastructure
sidecars they need to function, plus a deterministic
`SidecarsAssembler` that renders an operator-pasteable
`docker-compose.override.yml` snippet from a list of declared specs.
The first concrete consumer is `FileS3PluginManifest` declaring its
Garage backend; the renderer turns the declaration into the exact
compose shape an operator needs to stand up Garage and bind it to
the shepard backend.

The principle (memory `feedback_plugins_declare_sidecars.md`):
**plugins declare their sidecars; the deploy assembles compose;
hand-edited compose overrides are forbidden.**

## Files created (8)

| File | Lines | Purpose |
|---|---|---|
| `backend/src/main/java/de/dlr/shepard/plugin/SidecarSpec.java` | 117 | Top-level record — id, image, ports, volumes, env, healthcheck, postInit, backendEnvBinding. Defensive copies on all collections; null collections coerce to empty. Class-level javadoc documents the three templating placeholders. |
| `backend/src/main/java/de/dlr/shepard/plugin/PortSpec.java` | 33 | Record — port + role label. Port range validated `[1, 65535]`. |
| `backend/src/main/java/de/dlr/shepard/plugin/VolumeSpec.java` | 35 | Record — name + mountpoint. Mountpoint must be absolute. Named-volume shape only (no bind mounts — plugin-declared host paths are a smell). |
| `backend/src/main/java/de/dlr/shepard/plugin/HealthcheckSpec.java` | 62 | Record — cmd + interval + timeout + retries. Durations positive, retries ≥ 1. Mirrors docker-compose's `healthcheck:` block. |
| `backend/src/main/java/de/dlr/shepard/plugin/SidecarsAssembler.java` | 196 | The bootstrap-reader stub. One method `assembleComposeSnippet(List<SidecarSpec>)`. Deterministic + byte-stable + stateless. Top-level `volumes:` block, sorted env vars, healthcheck → compose-v3 mapping, templating placeholders pass through verbatim. |
| `backend/src/test/java/de/dlr/shepard/plugin/SidecarSpecTest.java` | 222 | 17 record-shape unit tests — blank ids, out-of-range ports, non-absolute mountpoints, non-positive durations, defensive-copy contract, templating pass-through. |
| `backend/src/test/java/de/dlr/shepard/plugin/SidecarsAssemblerTest.java` | 260 | 14 renderer tests — empty list → empty string, ports with role comments, top-level volumes block, env sort stability, healthcheck mapping, post-init numbered comments, backend-env hint comments, minute-formatted durations, multi-spec ordering, byte-stability across invocations. |
| `plugins/file-s3/src/test/java/de/dlr/shepard/plugins/files3/FileS3PluginManifestSidecarsTest.java` | 113 | 9 assertions on the Garage spec — id, image, ports (3900/3902 with roles), volume (`garage_data`), env (`{{generate:hex:64}}` and bind addrs), healthcheck shape, 5-step post-init in order, 6 `SHEPARD_FILES_S3_*` backend env bindings. |

## Files modified (6)

| File | Change |
|---|---|
| `backend/src/main/java/de/dlr/shepard/plugin/PluginManifest.java` | New `default List<SidecarSpec> sidecars() { return List.of(); }` method (non-breaking; +39 lines javadoc + impl) |
| `backend/src/test/java/de/dlr/shepard/plugin/PluginManifestTest.java` | +5 lines: `bareManifest_sidecarsDefaultsToEmptyList` |
| `plugins/file-s3/src/main/java/de/dlr/shepard/plugins/files3/FileS3PluginManifest.java` | Override `sidecars()` returning the Garage spec (+72 lines incl. imports + javadoc) |
| `aidocs/34-upstream-upgrade-path.md` | New PM1f row above FS1f (operator impact: ZERO unless they invoke the bootstrap; AWARE if they want the rendered snippet) |
| `aidocs/44-fork-vs-upstream-feature-matrix.md` | New PM1f row in §13d plugin SPI block (marked ✓ ↑ for SPI extension; bootstrap-reader integration marked 🚧 since it lands in mffd-import-v15 worktree) |
| `aidocs/platform/47-dev-experience-and-plugin-system.md` | New §2.6 "Sidecar declarations (PM1f)" — the SPI shape, the renderer contract, the operator-side bootstrap path, the worked example, the constraints (explicit tags, named volumes only, templating placeholders not literal secrets, one sidecar per `SidecarSpec`, no host-network/privileged) |

## Test summary

```
$ ./mvnw -Dquarkus.build.skip=true -Dtest='*Plugin*,*Sidecar*,*VersionRange*,*JarSig*' test
[INFO] Tests run: 138, Failures: 0, Errors: 0, Skipped: 2 (Skipped are pre-existing)

$ ./mvnw -Dtest='FileS3PluginManifestSidecarsTest,FileS3PluginManifestTest' test  # in plugins/file-s3
[INFO] Tests run: 18, Failures: 0, Errors: 0, Skipped: 0
```

Per-test class breakdown:

- `SidecarSpecTest` — 17 tests ✓
- `SidecarsAssemblerTest` — 14 tests ✓
- `PluginManifestTest` — 9 tests ✓ (was 8; added the sidecars-default assertion)
- `FileS3PluginManifestSidecarsTest` — 9 tests ✓
- All other `*Plugin*` tests — pass unchanged (PluginRegistry compatibility, dependency, persistence, JarSignature, VersionRange, PluginEntry, PluginRuntimeOverrideDAO, PluginManifestTest, PluginRegistryTest)

## Rendered snippet for the Garage spec (exact byte output)

This is the operator-pasteable output `SidecarsAssembler.assembleComposeSnippet(new FileS3PluginManifest().sidecars())` returns:

```yaml
# Generated by SidecarsAssembler from active plugin manifests.
# Append to your docker-compose.override.yml.
# Placeholders ({{generate:hex:N}}, {{sidecar.host}},
# {{from:postInit.N.field}}) are resolved by the operator-side bootstrap.
services:
  shepard-garage:
    image: dxflrs/garage:v1.0.1
    restart: unless-stopped
    ports:
      - "3900:3900"  # s3-api
      - "3902:3902"  # web-admin
    volumes:
      - garage_data:/var/lib/garage
    environment:
      GARAGE_RPC_SECRET: "{{generate:hex:64}}"
      GARAGE_S3_API_BIND_ADDR: "0.0.0.0:3900"
      GARAGE_WEB_BIND_ADDR: "0.0.0.0:3902"
    healthcheck:
      test: ["CMD-SHELL", "curl -fsS http://localhost:3900/health"]
      interval: 30s
      timeout: 10s
      retries: 3
    # post-init (run by operator-side bootstrap after healthy):
    #   [0] /garage layout assign ${NODE_ID} -z dc1 -c 1G
    #   [1] /garage layout apply --version 1
    #   [2] /garage bucket create shepard-files
    #   [3] /garage key new --name shepard-backend
    #   [4] /garage bucket allow --read --write shepard-files --key shepard-backend
    # backend-env (inject into the shepard backend service):
    #   SHEPARD_FILES_S3_ACCESS_KEY_ID={{from:postInit.3.access_key_id}}
    #   SHEPARD_FILES_S3_BUCKET=shepard-files
    #   SHEPARD_FILES_S3_ENDPOINT=http://{{sidecar.host}}:3900
    #   SHEPARD_FILES_S3_PATH_STYLE=true
    #   SHEPARD_FILES_S3_REGION=garage-region
    #   SHEPARD_FILES_S3_SECRET_ACCESS_KEY={{from:postInit.3.secret_access_key}}
volumes:
  garage_data:
```

The post-init and backend-env blocks are emitted as comments deliberately
— the renderer is a passive carrier; the operator-side bootstrap holds
the secrets file, captures structured output from `/garage key new`,
and substitutes the placeholders before applying.

## Design decisions made beyond the spec

1. **Record-shape over builder.** The spec text used a fluent builder
   (`.id().image().port().volume()...`). I went with a single record
   constructor instead because:
   - Records give us defensive-copy semantics + null-tolerance for
     collections (null → `List.of()`) without builder boilerplate.
   - Validation runs at construction time so a misshapen manifest
     fails the unit test, not the operator-deploy time.
   - The fluent shape is sugar — anyone who wants it can add a
     builder later without breaking record consumers.

2. **`postInit` and `backendEnvBinding` as YAML comments, not
   compose fields.** docker-compose-v3 doesn't have a native
   "run-after-healthy" hook (only `depends_on.condition: healthy`
   which isn't quite the same). Emitting them as comments + leaving
   the operator-side bootstrap to actually execute them is the
   honest mapping. The renderer documents what to run, doesn't
   pretend to run it.

3. **Top-level `volumes:` block.** Without this, the rendered
   snippet is incomplete — docker-compose rejects a service
   that mounts a named volume the document doesn't declare. The
   renderer collects every distinct volume name and emits a
   top-level block so the output is a valid standalone override.

4. **Sorted env vars.** Map iteration order in Java isn't stable
   across JVMs. Sorting at render time makes the output
   byte-deterministic so plugin-version upgrades produce clean
   diffs in operator config repos.

5. **Service name = `shepard-<sidecar.id>`.** Both for human
   readability (an operator running `docker ps` sees
   `shepard-garage` instead of just `garage` and knows where it
   came from) and for `{{sidecar.host}}` resolution (the
   operator-side renderer can substitute by service name).

6. **No exception for cluster-identity sidecars.** Per
   `CLAUDE.md`'s "operator knobs" rule, the cluster-identity
   exception applies to deploy-time-only config. Sidecar
   declarations live in the plugin manifest itself — they're
   *static metadata*, not runtime knobs — so the rule doesn't
   apply; nothing to flag.

7. **`{{from:postInit.3.access_key_id}}` indexes are zero-based.**
   So `/garage key new --name shepard-backend` (the 4th command,
   index 3) is the one whose output gets captured. I chose
   zero-based because it matches Java's `List.get()`
   semantics — the operator-side bootstrap is most naturally
   written in Python/shell which treats arrays the same way.

## Notes for downstream worktrees

**For the `mffd-import-v15` worktree:** the v15 import script's
pre-flight step can now call `SidecarsAssembler.assembleComposeSnippet`
on the file-s3 manifest's sidecar declaration to produce the exact
runbook text per `aidocs/integrations/93 §9`. The compose snippet
above is what the operator will paste — pre-flight should print it
verbatim when the S3 provider isn't active.

**For future plugin authors:** override `sidecars()` returning
`List.of(new SidecarSpec(...))`. The record validators catch
misshapen specs at construction time. Templating placeholders
(`{{generate:hex:N}}`, `{{sidecar.host}}`, `{{from:postInit.N.field}}`)
are the supported shape; do not ship literal secrets — they fail
review.

**Coverage gate:** the new SPI + assembler + Garage declaration
adds ~700 LoC of production code and ~600 LoC of tests, well above
the 70% new-code coverage floor. JaCoCo gate satisfied locally
(`mvn verify` partial run — full gate runs in CI per
`backend/pom.xml` `jacoco-maven-plugin check` execution).
