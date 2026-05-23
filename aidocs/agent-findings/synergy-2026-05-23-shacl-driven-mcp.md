---
stage: fragment
last-stage-change: 2026-05-23
---

# S-07 — SHACL × MCP tools × ShapesValidateRest: one validator, two surfaces

## Synergy

The same SHACL shape that drives a Vue form auto-publishes as a
typed MCP tool. `ShapesValidateRest` is the single validator both
the UI POST and the MCP `tools/call` route through. An ontology PR
ships a UI form **and** an agent tool in the same commit — no code
changes. Compounding plugin growth: every new shape = a new
research tool callable by Claude / Cursor / OpenAI Agents AND a new
UI form, no MCP-server work, no Vue work.

## Elements (named anchors)

- **Shipped endpoint:**
  `backend/src/main/java/de/dlr/shepard/v2/shapes/resources/ShapesValidateRest.java`
  — its Javadoc at line 28 already declares the catalogue location
  at `/v2/templates?kind=view`.
- **Design (feature-defined):**
  `aidocs/semantics/95-shacl-templates-and-individuals.md`
  Part 1 (shapes as templates), Part 2 (shapes as views), Part 3
  (shapes as agent contracts).
- **Design (fragment):**
  `aidocs/semantics/98-shapes-views-and-process-model.md`
  (the `:ShepardTemplate` consolidation; templateKind enum).
- **Plugin (shipped):** `shepard-plugin-mcp` —
  `aidocs/platform/30-mcp-plugin-design.md`,
  `aidocs/platform/88-quarkus-mcp-server-migration.md`, MCP SSE at
  `/mcp/sse` (Zoraxy + Keycloak PKCE).
- **External standard:** MCP `tools/list` + `tools/call` — JSON-RPC
  with `inputSchema: object` per tool.

## Why this is non-obvious

- The SHACL design (aidocs/95 Part 3) calls shapes "agent contracts"
  — but the MCP plugin (aidocs/30, aidocs/88) currently hard-codes
  its tool list in Java. Shapes-as-agent-contracts is a future
  state that depends on the SHACL → MCP bridge nobody has written.
- The SHACL → JSON-Schema converter exists in the wider ecosystem
  (SHACL-play, TopBraid, Schímatos research); the W3C SHACL 1.2 UI
  draft formalises form generation. The MCP tool spec uses JSON
  Schema for `inputSchema`. These are the same shape, twice.
- The `ShapesValidateRest` endpoint exists, but is plumbed into the
  UI form path only. Routing the MCP `tools/call` arguments through
  the same validator means agents and users get the same
  validation discipline.
- The compounding effect: today every new plugin adds (a) a SHACL
  shape, (b) a Vue form, (c) maybe an MCP tool. With this synergy
  it adds (a) only.
- DLR institutional strategy already points to this — the
  D4 mandate (`project_dlr_institutional_strategy`) is "MCP /
  Claude as a first-class research interface." This synergy makes
  EVERY future plugin Claude-ready by default.

## Concrete output

### 1. MCP tool list generated from the SHACL catalogue

```http
POST /v2/mcp/rpc
Content-Type: application/json

{"jsonrpc":"2.0","id":1,"method":"tools/list"}
```

Implementation: the MCP plugin queries
`GET /v2/templates?kind=mcp-tool` (or `?kind=any` filtered to shapes
that carry `shp:exposedAsTool true`); the response is the SHACL
catalogue. The plugin maps each shape to an MCP tool descriptor:

```json
{
  "name": "annotate_data_object",
  "description": "Attach a typed semantic annotation to a DataObject",
  "inputSchema": {
    "type": "object",
    "properties": {
      "dataObjectAppId": { "type": "string", "format": "uuid" },
      "propertyIRI":     { "type": "string", "format": "uri" },
      "valueIRI":        { "type": "string", "format": "uri" },
      "valueText":       { "type": "string" }
    },
    "required": ["dataObjectAppId", "propertyIRI"]
  }
}
```

The `inputSchema` is the SHACL shape transformed by a reusable
SHACL-to-JSON-Schema function (the SHACL-play algorithm is
sufficient).

### 2. Unified validation path

```
                       ┌──────────────────────┐
   Vue form submit ───►│                      │
                       │  ShapesValidateRest  │───► entity write
   MCP tools/call ────►│  (SHACL evaluator)   │
                       │                      │
                       └──────────────────────┘
```

Both paths hit the same `validate(shape, payload)` JAX-RS handler.
Validation errors come back as RFC 7807 `application/problem+json`
on the HTTP side; the MCP plugin transcribes them to MCP error
envelopes. One source of truth for "what's valid input."

### 3. Tool naming convention

Shape IRI: `shp:AnnotateDataObjectShape`
MCP tool name: `annotate_data_object`

Auto-derived by lowercasing + snake_casing the local name of the
shape IRI. Operators can override via `shp:mcpToolName` on the
shape if needed.

### 4. Plugin-author rule

A plugin author who wants to expose a new agent tool ships ONE file
under `META-INF/shepard/shapes/<plugin>/<shape>.ttl`. On plugin
enable:

- The shape registers in the SHACL catalogue.
- The MCP `tools/list` response includes the auto-generated tool
  descriptor.
- The Vue dynamic-form renderer (TPL2c per aidocs/95) picks the
  shape up and renders an editable form.

No Java code, no Vue code, no MCP plugin code. **One file = one
research feature, three surfaces.**

### 5. F(AI)²R audit trail (free)

The `ShapesValidateRest` endpoint already captures the call into
`ProvenanceCaptureFilter` (PROV1a, automatic). MCP-side calls flow
through the same provenance filter, marked with
`fair2r:AIActivity` because the MCP transport carries the agent
identity in the OIDC token (Keycloak PKCE per
`project_mcp_path`). Audit trail of "what shape did the AI
execute" is automatic — see also S-08.

## Real-world use case

**Persona:** an ontologist at DLR-RY adding a new annotation
shape for "PV-module efficiency rating." Today: they need a backend
developer to add the form fields, a frontend developer to render
them, and an MCP developer to expose them to Claude. Three PRs,
three review rounds, ~3 weeks. After this synergy: they write
`pv-efficiency.ttl` (a SHACL shape with ~15 lines), open a PR; the
shape lands; the form auto-appears; Claude can call
`annotate_pv_efficiency` immediately.

The compounding payoff: as Shepard's plugin ecosystem grows, the
*surface area increase* per new plugin is constant (one shape
file). For an institute running 30 plugins, that's 30
SHACL files instead of 30 × 3 = 90 PRs. The plugin growth
curve flattens.

For Trace3D (S-04): the view recipe (declared as a SHACL shape
with `templateKind: VIEW_RECIPE`) becomes an MCP tool the agent
can compose — "render TR-004 with the trace3d-with-video view"
becomes a one-call MCP request.

## External evidence

- **SHACL 1.2 *User Interfaces* W3C editor draft** —
  [w3c.github.io/data-shapes/shacl12-ui](https://w3c.github.io/data-shapes/shacl12-ui/)
  Takeaway: form generation from SHACL Core is being formalised by
  W3C; SHACL → form is no longer research, it's a draft standard.
- ***Schímatos: a SHACL-based Web-Form Generator for Knowledge
  Graph Editing*** —
  [researchgate.net/publication/343904877](https://www.researchgate.net/publication/343904877_Schimatos_a_SHACL-based_Web-Form_Generator_for_Knowledge_Graph_Editing)
  Takeaway: the SHACL → form pattern has a working open-source
  prototype showing the SHACL shape graph is sufficient to drive
  a CRUD UI; Shepard is reusing a proven pattern.
- **MCP specification — `tools/list` + `inputSchema`** —
  [modelcontextprotocol.io/docs](https://modelcontextprotocol.io/docs)
  Takeaway: MCP's tool descriptor uses JSON Schema directly for
  argument validation; no protocol bending needed.
- ***Generating MCP tools from OpenAPI* (Speakeasy, 2025)** —
  [speakeasy.com/mcp/tool-design/generate-mcp-tools-from-openapi](https://www.speakeasy.com/mcp/tool-design/generate-mcp-tools-from-openapi)
  Takeaway: the "fundamental mismatch" between REST endpoints and
  MCP tools (REST is resource-centric, MCP is workflow-centric) is
  exactly why SHACL-derived tools are the better path — SHACL
  shapes describe operations + semantic context, not just
  endpoints.
- ***Transforming SHACL Shape Graphs into HTML Applications for
  Populating Knowledge Graphs* (MDPI Information, 2024)** —
  [mdpi.com/2673-6470/5/4/56](https://www.mdpi.com/2673-6470/5/4/56)
  Takeaway: third-party validation that the pattern scales beyond
  trivial shapes; multi-form business-process composition is
  possible.

## Effort estimate

**M (medium).** Components:

- TPL1c (the 15-widget SHACL→form catalogue) — already designed in
  aidocs/95 Part 1; ~2 weeks (the front-end heavy lift).
- TPL2c (template renderer feature-flag) — ~1 week.
- SHACL → JSON-Schema converter (port from SHACL-play algorithm) —
  ~1 week.
- MCP `tools/list` plumbing into the SHACL catalogue — ~3-5 days
  on top of the existing MCP plugin.
- `ShapesValidateRest` is shipped; routing MCP calls through it —
  ~2-3 days.

Net: ~5-6 weeks across two surfaces (UI + MCP). After that, every
new SHACL shape = zero incremental work. The marginal cost of a
plugin's UI + agent surface drops to near-zero.

## Risk / counter-evidence

- SHACL → JSON Schema is lossy (SHACL is richer than JSON Schema).
  Mitigation: the lossy parts (closed shapes, advanced `sh:xone`)
  fall back to plain text fields with server-side validation; the
  agent can still call the tool — it just gets a 400 with the SHACL
  violation if the input doesn't match.
- The Speakeasy article (2025) flags that "REST → MCP" is the wrong
  level of abstraction. Same caution here: a single SHACL shape
  per MCP tool may be too granular. Mitigation: plugins can declare
  composite shapes (per aidocs/95 Part 5, `sh:node`) that map to
  workflow-level MCP tools.
- The W3C SHACL 1.2 UI draft is still draft; the form widget
  catalogue may change. Mitigation: stay on SHACL 1.1 + DASH (the
  TopBraid extension) for v1; adopt 1.2 widgets when the draft
  reaches CR.
- Schímatos and similar prototypes show SHACL-driven forms can hit
  cognitive overload for shapes with > 30 properties. Mitigation:
  composition via `sh:node` to break large shapes into nested
  sub-forms.
