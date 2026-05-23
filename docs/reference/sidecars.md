---
layout: default
title: Plugin sidecars SPI (reference)
permalink: /reference/sidecars/
audience: user
---
# Plugin sidecars — `PluginManifest.sidecars()`

shepard plugins frequently need **infrastructure sidecars** to
function — an S3 backend for the file-payload kind, a Kafka broker
for an event adapter, a Redis cache for a session-pinning plugin,
an external indexer for a search adapter. Until PM1f, an operator
activating a plugin had to discover the required sidecar from the
plugin's `install.md`, hand-edit a `docker-compose.override.yml`,
and hope they got the wiring right.

PM1f introduces a declarative shape:

> Plugins declare their sidecars; the deploy assembles compose;
> hand-edited compose overrides are forbidden.

Each `PluginManifest` exposes a `sidecars()` method returning a
list of `SidecarSpec` records. The shepard core walks every active
plugin, collects the specs, and (via `SidecarsAssembler`) renders
a deterministic compose snippet an operator can paste into their
deployment — or that a higher-level bootstrap can apply
automatically.

This page is the **plugin author's contract**: what to declare,
what gets templated, and how the operator-side renderer resolves
the placeholders.

## The SPI

```java
package de.dlr.shepard.plugin;

public interface PluginManifest {
    // ... id(), version(), shepardCompatibility(), title(), ...

    /**
     * PM1f addition. Default returns an empty list.
     */
    default List<SidecarSpec> sidecars() {
        return List.of();
    }
}
```

Plugins that need no sidecar inherit the default and ship
unchanged. Plugins that do need one return a `SidecarSpec`.

## `SidecarSpec`

```java
public record SidecarSpec(
    String id,                          // stable, lowercase, hyphen-separated
    String image,                       // OCI image with explicit tag
    List<PortSpec> ports,
    List<VolumeSpec> volumes,
    Map<String, String> env,            // sidecar env vars
    HealthcheckSpec healthcheck,
    List<String> postInit,              // shell commands run after healthy
    Map<String, String> backendEnvBinding  // env injected into the backend
) { ... }
```

Companion records:

```java
public record PortSpec(int port, String role) { }
public record VolumeSpec(String name, String mountPath) { }
public record HealthcheckSpec(
    String command,
    Duration interval,
    Duration timeout,
    int retries
) { }
```

The `role` label on `PortSpec` is the wiring hint the
`backendEnvBinding` map keys off — the operator-side renderer
substitutes the sidecar's compose service name + this port into
the backend container's env block.

## Templating placeholders

Three placeholder forms are honoured at render time. Resolution
happens in the **operator-side bootstrap**, not in the plugin or
the core — the spec stays portable across compose / kubernetes /
nomad.

| Placeholder | Meaning |
|---|---|
| `{{generate:hex:N}}` | Generate N hex characters of random entropy on **first** activation; persist to a secrets file; reuse on subsequent renders. Used for one-shot credentials like `GARAGE_RPC_SECRET`. |
| `{{sidecar.host}}` | Resolves to the docker-compose service name of this sidecar at render time. The backend uses this as the hostname for its env binding. |
| `{{from:postInit.N.field}}` | Captured from the N-th post-init command's structured output. For example, a `garage key new --name shepard-backend` command emits an access key id and secret on stdout — the renderer parses them and substitutes `{{from:postInit.3.access_key_id}}` and `{{from:postInit.3.secret_access_key}}`. |

These placeholders are intentionally string-shaped. A compose
renderer treats them one way; a kubernetes operator another. The
declaration is portable.

## Worked example — `file-s3` declares Garage

```java
// plugins/file-s3/src/main/java/de/dlr/shepard/plugins/files3/FileS3PluginManifest.java
@Override
public List<SidecarSpec> sidecars() {
    return List.of(
        new SidecarSpec(
            "garage",
            "dxflrs/garage:v1.0.1",
            List.of(
                new PortSpec(3900, "s3-api"),
                new PortSpec(3902, "web-admin")
            ),
            List.of(new VolumeSpec("garage_data", "/var/lib/garage")),
            Map.of(
                "GARAGE_RPC_SECRET",     "{{generate:hex:64}}",
                "GARAGE_S3_API_BIND_ADDR", "0.0.0.0:3900",
                "GARAGE_WEB_BIND_ADDR",    "0.0.0.0:3902"
            ),
            new HealthcheckSpec(
                "curl -fsS http://localhost:3900/health",
                Duration.ofSeconds(30),
                Duration.ofSeconds(10),
                3
            ),
            List.of(
                "/garage layout assign ${NODE_ID} -z dc1 -c 1G",
                "/garage layout apply --version 1",
                "/garage bucket create shepard-files",
                "/garage key new --name shepard-backend",
                "/garage bucket allow --read --write shepard-files --key shepard-backend"
            ),
            Map.of(
                "SHEPARD_FILES_S3_ENDPOINT",          "http://{{sidecar.host}}:3900",
                "SHEPARD_FILES_S3_REGION",            "garage-region",
                "SHEPARD_FILES_S3_PATH_STYLE",        "true",
                "SHEPARD_FILES_S3_BUCKET",            "shepard-files",
                "SHEPARD_FILES_S3_ACCESS_KEY_ID",     "{{from:postInit.3.access_key_id}}",
                "SHEPARD_FILES_S3_SECRET_ACCESS_KEY", "{{from:postInit.3.secret_access_key}}"
            )
        )
    );
}
```

What the operator-side bootstrap does with that:

1. Mints a fresh 64-hex-char `GARAGE_RPC_SECRET` and stashes it
   in `/etc/shepard/secrets/garage_rpc_secret` (or whatever
   secrets path the deploy uses).
2. Renders a `services.garage:` block in the compose override
   pinned to `dxflrs/garage:v1.0.1`, with the right port + volume
   + healthcheck.
3. Waits for the healthcheck to pass.
4. Runs the five `postInit` commands inside the Garage container.
   Parses the `garage key new` output to extract the access key id
   + secret.
5. Adds the six `SHEPARD_FILES_S3_*` env vars to the **backend**
   container's environment, substituting `garage` for
   `{{sidecar.host}}` and the captured key fields for the
   `{{from:postInit.3.*}}` placeholders.
6. `docker compose up -d` finishes the activation.

Today the bootstrap is a manual runbook
([Garage S3 sidecar activation]({{ '/ops/garage-activation-runbook' | relative_url }}))
the operator follows by hand. The PM1f declaration is the source
of truth; the runbook is its current manual realisation.

## `SidecarsAssembler`

`de.dlr.shepard.plugin.SidecarsAssembler` is the in-process
renderer that walks the active plugins and emits a deterministic
compose-v3 snippet. The output is byte-stable — the same set of
declared sidecars always produces the same output. That's what
makes the snippet diff-friendly across plugin upgrades.

The assembler does **not** resolve placeholders. It emits them
verbatim, leaving resolution to the operator-side renderer (or to
the future "shepard bootstrap" CLI tool tracked under
`aidocs/integrations/93 §9`).

## Constraints

- `id` must be non-blank, lowercase, hyphen-separated.
- `image` must carry an explicit tag — no `:latest`, no implicit
  tag. ADR-0020.
- `ports`, `volumes`, `postInit` may be empty lists; the records
  defensively copy what's passed.
- A plugin that declares no sidecar (the default) is **the common
  case**. Most plugins compute against shepard core's own
  facilities; only those that need a new external service ship a
  `SidecarSpec`.

## Why not hand-edit compose?

The principle behind PM1f (memory
`feedback_plugins_declare_sidecars.md`): hand-edited compose
overrides drift the moment plugins upgrade. The operator forgets
which keys are required, the plugin author forgets which version
of the sidecar is known-compatible, and a year later nobody can
reproduce the working stack.

Declaring the sidecar **in the plugin** binds the requirement to
the plugin's release cadence. Upgrade the plugin, get the new
sidecar version automatically. The operator doesn't need to read
release notes for the sidecar separately.

## See also

- [Plugins reference]({{ '/reference/plugins' | relative_url }}) — the
  broader `PluginManifest` SPI.
- [File storage]({{ '/reference/file-storage' | relative_url }}) —
  `shepard-plugin-file-s3` is the first concrete consumer of PM1f.
- [Garage S3 sidecar activation runbook]({{ '/ops/garage-activation-runbook' | relative_url }})
  — the manual realisation of the `file-s3` `SidecarSpec` until
  the operator-side bootstrap ships.
- `aidocs/platform/47-dev-experience-and-plugin-system.md §2.6` —
  the SPI design doc.
- `backend/src/main/java/de/dlr/shepard/plugin/SidecarSpec.java` —
  the record itself, fully javadocced.
