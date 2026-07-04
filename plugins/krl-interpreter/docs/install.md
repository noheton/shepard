# shepard-plugin-krl-interpreter — install

**Audience.** Operators wiring the plugin into a Shepard deployment.

> **Status (KRL-INTERPRETER-04 shipped).** The sidecar containerisation
> (`Dockerfile` + `compose-profile.yml` + FastAPI REST) is now in the
> plugin module. The backend wiring (`/v2/krl/interpret` resource +
> `:KrlInterpretActivity` audit trail) remains scoped to
> `KRL-INTERPRETER-05`; the runtime-mutable `:KrlInterpreterConfig`
> singleton is tracked as **KRL-CONFIG-1** (deferred to tier-2).
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

### Healthcheck endpoint

`GET /health` returns `{"status": "ok", "version": "<x.y.z>"}` on a
healthy sidecar. Both the in-container `HEALTHCHECK` and the compose
healthcheck read this; depend-on conditions in downstream services may
gate on this signal once the -05 backend wiring lands.

### Known pitfalls (sidecar)

- **Tier-1 `:KrlInterpreterConfig` admin singleton is deferred.** Flipping
  IK tolerance, time step, or motion duration requires a container
  restart with new env. Tier-2 will land the runtime PATCH endpoint
  (tracked as KRL-CONFIG-1).
- **Async `/interpret/async` returns 501.** The sync `/interpret`
  endpoint handles all tier-1 traffic; sidecar IK is fast enough on a
  6-DOF arm (~12 ms / pose) that even a 5000-pose program completes in
  ~60 s synchronously. The async polling shape is documented but not
  implemented at tier-1.
- **Frames on the wire are metres + radians.** The backend (-05) is
  responsible for converting from the KRL `.src`'s native millimetres
  before posting to the sidecar. The composer applies the mm -> m
  conversion at the IR -> IK boundary internally; if you send a
  pre-resolved override `baseFrame` / `toolFrame` in the request, it
  must already be in metres.
