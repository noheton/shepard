# shepard-plugin-krl-interpreter — quickstart

**Audience.** A user who wants to parse a `.src` to an IR in 5 lines
of Python.

> Note: the in-Shepard "click `.src` → Run / preview" UI lands in
> `KRL-INTERPRETER-06`. This page covers only the Python-level parse
> shipped in `KRL-INTERPRETER-02`. The sidecar + REST surface for
> end-to-end UI use lands in `-04` and `-05`.

---

## Parse a .src file in 5 lines

```python
from krl_interpreter import parse

result = parse(open("Ply_5_layup.src").read(), filename="Ply_5_layup.src")
print(f"module={result.program.module_name}, statements={len(result.program.statements)}")
print(f"unsupported={[(u.construct, u.line) for u in result.unsupported]}")
for warning in result.warnings:
    print(warning)
```

## Inspect the motion plan

```python
from krl_interpreter import parse
from krl_interpreter.parser.ir import Motion, MotionKind

result = parse(open("Ply_5_layup.src").read())

for stmt in result.program.statements:
    if isinstance(stmt, Motion):
        x, y, z = stmt.target.x, stmt.target.y, stmt.target.z
        print(f"L{stmt.line}  {stmt.kind.value}  ({x}, {y}, {z})")
```

## Find every $BASE switch

```python
from krl_interpreter import parse
from krl_interpreter.parser.ir import BaseToolSwitch, FrameTarget

result = parse(open("frame_switching.src").read())
base_switches = [
    s for s in result.program.statements
    if isinstance(s, BaseToolSwitch) and s.target is FrameTarget.BASE
]
print(f"Found {len(base_switches)} $BASE assignments")
```

## What the parser does NOT do

- It does not evaluate `FOR` bounds, `IF` conditions, or arithmetic
  expressions — those are forward-strings on the IR.
- It does not resolve `#INCLUDE` directives (tier-1: single file).
- It does not call IK or emit a trajectory — that's
  `KRL-INTERPRETER-03`.

For the end-to-end flow (.src + URDF → animated trajectory in the
browser) see the design doc at
[`aidocs/integrations/117-krl-interpreter.md`](../../../aidocs/integrations/117-krl-interpreter.md).
