# shepard-plugin-krl-interpreter — install

**Audience.** Operators wiring the plugin into a Shepard deployment.

> **Status.** KRL-INTERPRETER-04 (sidecar), KRL-INTERPRETER-05
> (backend REST `POST /v2/krl/interpret`), and KRL-INTERPRETER-06
> (frontend UI) are all **shipped**. The runtime-mutable
> `:KrlInterpreterConfig` singleton (admin REST + CLI parity) is
> deferred to **KRL-CONFIG-1** (tier-2). Until then, all knobs are
> deploy-time-only via `application.properties` / env vars.
>
> See [`aidocs/integrations/117-krl-interpreter.md §6` + `§10`](../../../aidocs/integrations/117-krl-interpreter.md)
> for the protocol contract this implementation satisfies.

---

## Tier-1 install (today)

```bash
cd plugins/krl-interpreter
pip install -e .
```

This installs the Python package only. Optional dev dependencies for
running the test suite:

```bash
pip install -e ".[dev]"
python -m pytest --cov=krl_interpreter --cov-report=term
```

Runtime dependency tree (tier-1):

| Package                    | Pinned version | Why                                       |
| -------------------------- | -------------- | ----------------------------------------- |
| `antlr4-python3-runtime`   | `==4.13.2`     | Must match the ANTLR tool used to generate `parser/grammar/generated/`. |

Python target: `>=3.11` (CPython 3.11, 3.12, 3.13 tested).

## Verifying the install

```python
import krl_interpreter
print(krl_interpreter.__version__)  # 0.1.0

from krl_interpreter import parse
result = parse("DEF p()\nPTP {X 0, Y 0, Z 0, A 0, B 0, C 0}\nEND\n")
assert result.program.module_name == "p"
```

## Regenerating the grammar (rare)

The `parser/grammar/generated/*` files are committed; CI does not
regenerate. If you edit `KrlLexer.g4` or `KrlParser.g4`:

```bash
cd plugins/krl-interpreter/krl_interpreter/parser/grammar
java -jar /path/to/antlr-4.13.2-complete.jar \
  -Dlanguage=Python3 \
  -o generated -Xexact-output-dir \
  KrlLexer.g4 KrlParser.g4
```

Then bump `antlr4-python3-runtime` in `pyproject.toml` to match the
ANTLR tool version.

## Known pitfalls

- **Offline ≠ as-executed.** Per `aidocs/integrations/117 §13.1`, the
  interpreter is a *structural* preview, not a fidelity replica of the
  KRC controller's runtime motion. Trajectories should always be
  labelled "interpreter-resolved offline replay" downstream — the
  reference UI in `-06` and any export pipeline must carry this label.
  See the `KRL-INTERPRETER-AUDIT-LABEL` sub-row.
- **SPS programs not supported (tier-1).** A `.src` containing an
  `SPS` block parses, but the SPS section becomes an
  `UnsupportedConstruct` with reason "SPS (parallel
  submit-interpreter) has no offline equivalent". The downstream REST
  surface returns `501` per `aidocs/integrations/117 §3.4`.
- **`.kop` WorkVisual bundles are not auto-extracted.** Users upload
  `.src` + `.dat` separately.
- **`#INCLUDE` is single-file at tier-1.** A `#INCLUDE` directive
  inside a `.src` does not pull in additional files; tier-2 will
  accept `srcFileAppIds[]` arrays.

## Sidecar bring-up (KRL-INTERPRETER-04)

The sidecar is an opt-in Docker service activated via the
`krl-interpreter` compose profile. The plugin declares its service in
`plugins/krl-interpreter/compose-profile.yml`; the operator overlays it
on the main infrastructure compose file:

```bash
docker compose \
  -f infrastructure/docker-compose.yml \
  -f plugins/krl-interpreter/compose-profile.yml \
  --profile krl-interpreter \
  up -d krl-interpreter-sidecar
```

### Verifying the sidecar

The sidecar exposes **no host port** by design (the backend reaches it
via internal compose DNS at `http://krl-interpreter-sidecar:8000`).
For operator introspection:

```bash
docker compose exec krl-interpreter-sidecar curl --silent http://localhost:8000/health
# {"status":"ok","version":"0.1.0"}
```

### Env-var reference

All knobs are deploy-time-only at tier-1. The runtime-mutable
`:KrlInterpreterConfig` Neo4j singleton is **deferred** to tier-2
(tracked as **KRL-CONFIG-1** in `aidocs/16-dispatcher-backlog.md`).

| Variable                    | Default              | Meaning                                           |
| --------------------------- | -------------------- | ------------------------------------------------- |
| `PORT`                      | `8000`               | HTTP port the sidecar binds.                      |
| `HOST`                      | `0.0.0.0`            | Bind host.                                        |
| `MAX_BODY_SIZE`             | `5242880` (5 MiB)    | Informational; enforced upstream by reverse proxy. |
| `KRL_IK_TOLERANCE`          | `1e-3`               | Default IK tolerance in metres.                   |
| `KRL_MAX_ITERATIONS`        | `100`                | Default IK iteration cap (informational on ikpy). |
| `KRL_TIME_STEP_DEFAULT`     | `0.01`               | Default trajectory sample interval (s).           |
| `KRL_MOTION_DURATION`       | `1.0`                | Tier-1 fixed per-motion duration (s).             |
| `KRL_MAX_IR_ITERATIONS`     | `100000`             | Safety cap on `WHILE` / `LOOP` unrolling.         |
| `LOG_LEVEL`                 | `info`               | Uvicorn log level.                                |

The compose-profile reads `KRL_SIDECAR_PORT`, `KRL_SIDECAR_HOST`,
`KRL_LOG_LEVEL`, `KRL_MAX_BODY_SIZE`, and the `KRL_*` knobs above from
the operator env file (with the defaults shown above). The compose-prefix
caveat:

| Variable                      | Default                 | Meaning                                |
| ----------------------------- | ----------------------- | -------------------------------------- |
| `KRL_SIDECAR_DOCKER_NETWORK`  | `infrastructure_shepard` | Compose-prefixed network the sidecar joins. Override if your compose project name is non-default. |

### Reverse-proxy / path mount

The sidecar exposes **no browser UI at tier-1** (REST-only). A future
admin pane (filed in `aidocs/16` follow-on rows) will land at
`/krl-interpreter/` per the
[`## Always: mount plugin UI sidecars as paths, not subdomains`](../../../CLAUDE.md)
rule. When that ships, the sidecar's `app.py` will need a `root_path`
override + a corresponding Caddyfile block; the
`docs/install.md §4` (this section) will be expanded then.

### Backend `application.properties` keys (KRL-INTERPRETER-05)

These three keys configure the Shepard backend's sidecar client.
Set them in `application.properties` or as environment variables
(using MicroProfile Config's `_` → `.` env-var mapping, e.g.
`SHEPARD_KRL_SIDECAR_URL`):

| Key | Default | Description |
| --- | ------- | ----------- |
| `shepard.krl.sidecar.url` | `http://krl-interpreter-sidecar:8000` | Base URL of the KRL interpreter sidecar. Override when the sidecar runs on a non-default host or port. |
| `shepard.krl.sidecar.timeout-seconds` | `120` | Per-call HTTP timeout in seconds. Increase for very large programs (> 3000 poses). Returns 504 when exceeded. |
| `shepard.krl.sidecar.max-body-size-mb` | `16` | Guard against runaway payloads. The backend rejects requests whose summed file payloads exceed this value (MiB). |

> **Runtime-mutable config coming in KRL-CONFIG-1.** When KRL-CONFIG-1
> ships, operators will be able to flip these knobs via
> `GET/PATCH /v2/admin/plugins/krl/config` without a restart.
> Until then, changing any of these values requires a backend restart.

### Healthcheck endpoint

`GET /health` returns `{"status": "ok", "version": "<x.y.z>"}` on a
healthy sidecar. Both the in-container `HEALTHCHECK` and the compose
healthcheck read this. The backend (`POST /v2/krl/interpret`) returns
502 when the sidecar is unreachable — this is the documented expected
behaviour, not a backend error.

### Known pitfalls (sidecar)

- **Runtime config requires a restart at tier-1.** Flipping IK
  tolerance, time step, or motion duration requires a sidecar
  container restart with updated env vars. The runtime PATCH endpoint
  is tracked as KRL-CONFIG-1 (tier-2).
- **Async `/interpret/async` returns 501.** The sync `/interpret`
  endpoint handles all tier-1 traffic; sidecar IK is fast enough on a
  6-DOF arm (~12 ms / pose) that even a 5000-pose program completes in
  ~60 s synchronously. The async polling shape is documented but not
  implemented at tier-1.
- **Frames on the wire are metres + radians.** The backend converts
  from the KRL `.src`'s native millimetres before posting to the
  sidecar. If you supply a pre-resolved override `baseFrame` /
  `toolFrame` in the `POST /v2/krl/interpret` request body, it must
  already be in metres + radians.
- **`timeseriesContainerAppId` must be pre-existing.** Create a
  `TimeseriesContainer` under the target DataObject before running the
  interpreter. Auto-mint of a per-DataObject default container is
  deferred to KRL-INTERPRETER-05-FOLLOWUP-AUTO-CONTAINER.
