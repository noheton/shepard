# shepard-plugin-krl-interpreter — install

**Audience.** Operators wiring the plugin into a Shepard deployment.

> **Tier-1 (KRL-INTERPRETER-02) is a parser library only.** There is
> no sidecar container, no REST surface, no admin endpoint, no
> compose-profile yet. Those land in `KRL-INTERPRETER-04` (sidecar
> containerisation) and `-05` (backend REST + Activity wiring).
>
> See [`aidocs/integrations/117-krl-interpreter.md §10`](../../../aidocs/integrations/117-krl-interpreter.md)
> for the eventual sidecar shape (`compose-profile.yml`, healthcheck,
> `:KrlInterpreterConfig` admin singleton, CLI parity).

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

## Future install (sidecar shape, deferred)

The eventual operator install shape — once `KRL-INTERPRETER-04`
ships — is:

```yaml
# Eventual compose-profile.yml. NOT shipped in KRL-INTERPRETER-02.
services:
  krl-interpreter:
    image: ghcr.io/dlr-shepard/krl-interpreter:0.1.0
    environment:
      KRL_LOG_LEVEL: INFO
      KRL_IK_TOLERANCE: 1e-3
    healthcheck:
      test: ["CMD", "python3", "-c", "import urllib.request; urllib.request.urlopen('http://localhost:8080/health').read()"]
```

The full shape is documented in `aidocs/integrations/117 §10`.
