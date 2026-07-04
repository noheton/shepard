# shepard-plugin-krl-interpreter

Offline interpreter for KUKA Robot Language (KRL) `.src` / `.dat`
programs. Parses the program, back-solves IK against a URDF, emits a
joint-angle trajectory for replay against `URDF-WEBVIEW-1`.

Design doc: [`aidocs/integrations/117-krl-interpreter.md`](../../aidocs/integrations/117-krl-interpreter.md).

Sub-rows in `aidocs/16-dispatcher-backlog.md`:

- `KRL-INTERPRETER-01-DESIGN` — design pass (done).
- `KRL-INTERPRETER-02-PARSER` — `pykrlparser` + tier-1 grammar.
- `KRL-INTERPRETER-03-IK` — **this PR.** `ikpy` back-solver + seed
  strategy + URDF loader.
- `KRL-INTERPRETER-04-SIDECAR` — FastAPI sidecar + container.
- `KRL-INTERPRETER-05-REST` — backend `POST /v2/krl/interpret`.
- `KRL-INTERPRETER-06-UI` — frontend "Run / preview" button.
- `KRL-INTERPRETER-07-MFFD-SHOWCASE` — end-to-end MFFD AFP demo.
- `KRL-INTERPRETER-08-DOCS` — three-pane plugin docs.

## Layout

```
krl_interpreter/
  ik/          # IK back-solver (KRL-INTERPRETER-03; this PR)
  parser/      # KRL parser (KRL-INTERPRETER-02; sibling worktree)
tests/
  ik_fixtures/ # URDF fixtures for IK tests
```

## Local dev

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -e ".[test]"
pytest -m "not slow"          # fast unit tests
pytest                         # includes KR210 benchmark (slow)
```
