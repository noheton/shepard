# shepard-plugin-krl-interpreter — reference

**Audience.** Plugin authors and power users who need to know what
KRL the parser handles, how the IR is shaped, and how warnings /
unsupported constructs surface.

**Status.** KRL-INTERPRETER-02 (parser layer only). Sidecar
containerisation lands in `KRL-INTERPRETER-04`; the REST surface lands
in `-05`. See [`aidocs/integrations/117-krl-interpreter.md`](../../../aidocs/integrations/117-krl-interpreter.md)
for the full system design.

---

## 1. What this module does

Given a KUKA `.src` (executable program) or `.dat` (companion data)
file, produce a Python intermediate representation (IR) that:

* preserves enough source structure to drive an IK back-solve
  (`KRL-INTERPRETER-03`);
* surfaces every unsupported tier-1 construct as a structured node
  (queryable later via the `krl_list_unsupported` MCP tool described
  in `aidocs/integrations/117 §9.2`);
* attaches `(line, column)` to every IR node for downstream audit
  trails.

It does **not** evaluate expressions, unroll loops, resolve IK, or
emit trajectories. Those are downstream layers.

## 2. KRL subset covered

Mirrors `aidocs/integrations/117 §4`. Tier-1 coverage:

| KRL construct                                  | Parser support  | IR node                          |
| ---------------------------------------------- | --------------- | -------------------------------- |
| `PTP <pose>` / `PTP_REL <pose>`                | full            | `Motion(kind=PTP|PTP_REL)`       |
| `LIN <pose>` / `LIN_REL <pose>`                | full            | `Motion(kind=LIN|LIN_REL)`       |
| `CIRC <aux>, <target>` / `CIRC_REL`            | full            | `Motion(kind=CIRC|CIRC_REL, aux)` |
| Motion options (`C_DIS`, `C_PTP`, …)           | captured raw    | `Motion.opts: list[str]`         |
| `WAIT SEC <n>`                                 | full            | `Wait(seconds=<n>)`              |
| `WAIT FOR <cond>`                              | **degraded**    | `Wait(condition=...)` + WARN     |
| `IF / THEN / ELSE / ENDIF`                     | full            | `If(condition, then, else)`      |
| `FOR <v>=<a> TO <b> [STEP <s>] / ENDFOR`       | full            | `For(var, start, end, step, body)` |
| `WHILE <cond> / ENDWHILE`                      | full            | `While(condition, body)`         |
| `LOOP / ENDLOOP` + `EXIT`                      | full            | `Loop(body)` + `Exit`            |
| `$BASE = <frame>` / `$TOOL = <frame>`          | full            | `BaseToolSwitch(target, frame)`  |
| `FRAME` literal `{X 100, Y 0, Z 200, A 0, B 0, C 0}` | full       | `FrameLiteral`                   |
| Sparse frame literal (subset of fields)        | full (defaults) | `FrameLiteral.extras['_missing_fields']` + INFO warning |
| `E6POS` literal (`E1`–`E6`)                    | full            | `E6PosLiteral`                   |
| `DECL <type> <name> [= <expr>]`                | full            | `VarDecl(type_name, name, initial)` |
| `<var> = <expr>`                               | full            | `Assign(var, expr)`              |
| `DEFDAT <name> PUBLIC?`                        | full            | `Program(is_data_file=True)`     |
| `BCO`                                          | **unsupported** | `UnsupportedConstruct + WARN`    |
| `SPS`                                          | **unsupported** | `UnsupportedConstruct + WARN`    |
| `INTERRUPT` / `ON ERROR` / `ON INTERRUPT`      | **unsupported** | `UnsupportedConstruct + WARN`    |
| `ANIN` / `ANOUT` (sensor IO)                   | **unsupported** | `UnsupportedConstruct + WARN`    |
| `CONTINUE` / `HALT`                            | **unsupported** | `UnsupportedConstruct + WARN`    |
| `#INCLUDE` cross-file resolution               | out of scope    | (tier-2)                         |
| `.kop` WorkVisual bundles                      | out of scope    | (operator extracts manually)     |

## 3. IR shape

```
Program
  ├─ module_name: str | None
  ├─ is_function: bool       # True for DEFFCT
  ├─ is_data_file: bool      # True for DEFDAT
  ├─ is_global: bool
  └─ statements: list[Statement]

Statement = Motion | Wait | If | For | While | Loop | Exit
          | Assign | VarDecl | BaseToolSwitch | UnsupportedConstruct

Motion(kind: MotionKind, target: Pose, aux: Pose | None, opts: list[str])
Wait(seconds: float | None, condition: str | None)   # exactly one set
If(condition: str, then_block: list[Statement], else_block: list[Statement])
For(var: str, start: str, end: str, step: str | None, body: list[Statement])
While(condition: str, body: list[Statement])
Loop(body: list[Statement])
Exit()
BaseToolSwitch(target: FrameTarget, frame: Pose)
Assign(var: str, expr: str)
VarDecl(type_name: str, name: str, initial: str | None)
UnsupportedConstruct(construct: str, reason: str, raw_text: str)

Pose = FrameLiteral | E6PosLiteral | VarRef
FrameLiteral(x, y, z, a, b, c, extras: dict)
E6PosLiteral(frame: FrameLiteral, e1..e6: float | None)
VarRef(name: str)
```

Every statement node also carries `line: int` and `column: int`.

Expression values (loop bounds, conditions, RHS of assignment) are
preserved as opaque source-text strings — the parser does not
evaluate them. The IK / interpret layer (`KRL-INTERPRETER-03`)
is the consumer that resolves them.

## 4. Warning + error surfaces

Three surfaces:

1. **`ParseResult.warnings`** — list of
   `krl_interpreter.errors.Warning`. Severity ∈ `INFO | WARN | ERROR`.
   Examples:
   * `INFO` — sparse frame literal (missing field defaults to 0).
   * `WARN` — `WAIT FOR` degraded; tier-1 unsupported construct
     (BCO / SPS / INTERRUPT / etc.).
   * `ERROR` — ANTLR4 syntax errors that did not abort the parse.

2. **`ParseResult.unsupported`** — list of `UnsupportedConstruct` IR
   nodes. This is the structured surface an audit lens or the MCP
   `krl_list_unsupported` tool consumes.

3. **`ParseError`** — raised only when the source is unparseable past
   the first statement (the sidecar's 400 case). Carries
   `line` + `column` from the first ANTLR4 syntax error.

## 5. Public Python API

```python
from krl_interpreter import parse, ParseResult, ParseError

result: ParseResult = parse(source_text, filename="Ply_5_layup.src")
result.program       # Program IR
result.warnings      # list[Warning]
result.unsupported   # list[UnsupportedConstruct]
```

Lower-level access for advanced use:

```python
from krl_interpreter.parser.walker import KrlIrBuilder
from krl_interpreter.parser.grammar.generated.KrlLexer import KrlLexer
from krl_interpreter.parser.grammar.generated.KrlParser import KrlParser
from antlr4 import CommonTokenStream, InputStream

lexer = KrlLexer(InputStream(source))
parser = KrlParser(CommonTokenStream(lexer))
tree = parser.program()
builder = KrlIrBuilder(filename="x.src")
program = builder.build(tree)
```

## 6. Grammar provenance

See [`krl_interpreter/parser/grammar/SOURCES.md`](../krl_interpreter/parser/grammar/SOURCES.md).
The `.g4` files are original work under MIT; regeneration is one
`java -jar antlr-4.13.2-complete.jar …` invocation.

## 7. Cross-references

* `aidocs/integrations/117-krl-interpreter.md` — full system design.
* `aidocs/integrations/113-urdf-viewer.md` — downstream consumer.
* `aidocs/integrations/110-file-format-parser-plugin.md §4.3` — RDK
  parse (sibling).
* `aidocs/data/85-coordinate-frame-tree.md` — frame schema the IK
  layer will resolve targets against.
