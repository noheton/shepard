/**
 * V2CONV-B6 — pure helpers for the visual template editor.
 *
 * The editor composes a `ShepardTemplate` (a SHACL shape) by picking semantic
 * predicates from the vocabulary palette. Its in-memory state is a small,
 * UI-friendly row model ({@link ShapeEditorState}); these helpers translate that
 * state to/from the two on-the-wire shapes:
 *
 *   1. The **build DSL** ({@link ShapeBuildRequest}) — POSTed to
 *      `/v2/shapes/build`, which the backend `ShaclShapeBuilder` compiles to
 *      canonical Turtle. Mirrors `ShapeBuildRequestIO` 1:1.
 *   2. The persisted template **body** (a JSON string) — the editor stores the
 *      DSL under `editorState` plus the compiled Turtle under `shapeGraph`
 *      (the convention `/shapes/validate` already reads via
 *      `extractShapeGraphFromTemplateBody`). Storing the editor state lets a
 *      later edit reopen the visual editor without reverse-engineering Turtle.
 *
 * All functions here are pure and Nuxt-free so they are unit-testable in
 * isolation (Vitest). No network, no `ref`s.
 *
 * Design: aidocs/platform/191-v2-surface-convergence.md §3.
 */

export const XSD = "http://www.w3.org/2001/XMLSchema#";

/** Common literal datatypes offered in the editor's datatype picker. */
export const DATATYPE_OPTIONS: { title: string; value: string }[] = [
  { title: "(none / IRI object)", value: "" },
  { title: "string", value: XSD + "string" },
  { title: "integer", value: XSD + "integer" },
  { title: "decimal", value: XSD + "decimal" },
  { title: "boolean", value: XSD + "boolean" },
  { title: "dateTime", value: XSD + "dateTime" },
  { title: "date", value: XSD + "date" },
  { title: "anyURI", value: XSD + "anyURI" },
];

/** One member of a property's `sh:in` value set. */
export interface InMemberRow {
  value: string;
  kind: "IRI" | "LITERAL";
  datatype?: string | null;
}

/** One property-shape row in the editor (a single predicate constraint). */
export interface PropertyRow {
  /** The predicate IRI (`sh:path`). Required. */
  path: string;
  /** Optional human label (palette-supplied) — display only, not serialised to the DSL. */
  label?: string | null;
  /** Literal datatype IRI (`sh:datatype`). Empty string / null = unset. */
  datatype?: string | null;
  /** `sh:minCount`. null = unset. */
  minCount?: number | null;
  /** `sh:maxCount`. null = unset. */
  maxCount?: number | null;
  /** `sh:in` value set. Empty = unset. */
  in?: InMemberRow[];
  /** Nested `sh:NodeShape` IRI (`sh:node`). Empty / null = unset. */
  node?: string | null;
}

/** The full editor state for one node shape. */
export interface ShapeEditorState {
  shapeIri?: string | null;
  targetClass?: string | null;
  closed: boolean;
  properties: PropertyRow[];
}

/** Wire shape for `POST /v2/shapes/build` (mirrors `ShapeBuildRequestIO`). */
export interface ShapeBuildRequest {
  shapeIri?: string | null;
  targetClass?: string | null;
  closed: boolean;
  properties: {
    path: string;
    datatype?: string | null;
    minCount?: number | null;
    maxCount?: number | null;
    in?: { value: string; kind: "IRI" | "LITERAL"; datatype?: string | null }[];
    node?: string | null;
  }[];
}

/** A fresh, empty editor state. */
export function emptyShapeEditorState(): ShapeEditorState {
  return { shapeIri: "", targetClass: "", closed: false, properties: [] };
}

/** A fresh property row pre-populated from a palette predicate. */
export function propertyRowFromPalette(p: {
  uri: string;
  label?: string | null;
  datatype?: string | null;
  cardinality?: string | null;
}): PropertyRow {
  return {
    path: p.uri,
    label: p.label ?? null,
    datatype: p.datatype ?? "",
    minCount: cardinalityToMin(p.cardinality),
    maxCount: cardinalityToMax(p.cardinality),
    in: [],
    node: "",
  };
}

/**
 * Map a free-form cardinality hint (e.g. "1", "1..1", "0..*", "exactly one",
 * "required") to a `sh:minCount`. Returns null when no sensible default exists.
 */
export function cardinalityToMin(card?: string | null): number | null {
  if (!card) return null;
  const c = card.trim().toLowerCase();
  if (c === "required" || c === "1" || c.startsWith("1..") || c === "exactly one") return 1;
  if (c.startsWith("0..") || c === "optional" || c === "0") return 0;
  const m = c.match(/^(\d+)/);
  return m ? Number(m[1]) : null;
}

/** Map a cardinality hint to a `sh:maxCount`. `*` / `n` → null (unbounded). */
export function cardinalityToMax(card?: string | null): number | null {
  if (!card) return null;
  const c = card.trim().toLowerCase();
  if (c === "1" || c === "0..1" || c === "1..1" || c === "exactly one") return 1;
  if (c.endsWith("..*") || c.endsWith("..n") || c === "*") return null;
  const m = c.match(/\.\.(\d+)$/);
  return m ? Number(m[1]) : null;
}

/** Trim + nullify blank strings (so the DSL stays clean — no empty fields). */
function nz(v: string | null | undefined): string | null {
  const t = (v ?? "").trim();
  return t.length > 0 ? t : null;
}

/**
 * Serialise editor state to the `/v2/shapes/build` request DSL. Drops blank
 * optional fields and empty `sh:in` lists so the compiled Turtle is minimal.
 * Property rows whose `path` is blank are dropped (the editor disables Build
 * until every row has a path, but this keeps the serialiser robust).
 */
export function editorStateToBuildRequest(state: ShapeEditorState): ShapeBuildRequest {
  return {
    shapeIri: nz(state.shapeIri),
    targetClass: nz(state.targetClass),
    closed: !!state.closed,
    properties: (state.properties ?? [])
      .filter((p) => nz(p.path))
      .map((p) => {
        const members = (p.in ?? [])
          .filter((m) => nz(m.value))
          .map((m) => ({
            value: m.value.trim(),
            kind: m.kind === "IRI" ? ("IRI" as const) : ("LITERAL" as const),
            datatype: m.kind === "IRI" ? null : nz(m.datatype),
          }));
        return {
          path: p.path.trim(),
          datatype: nz(p.datatype),
          minCount: p.minCount ?? null,
          maxCount: p.maxCount ?? null,
          in: members.length > 0 ? members : null,
          node: nz(p.node),
        };
      }),
  };
}

/**
 * The persisted template body. The editor stores both the editor state (so a
 * future edit reopens the visual editor) and the compiled shape graph (so
 * `/shapes/validate` and create-form generation read it directly).
 */
export interface TemplateShapeBody {
  /** The editor's row model — reopened by the visual editor on edit. */
  editorState: ShapeEditorState;
  /** Compiled SHACL Turtle (read by `extractShapeGraphFromTemplateBody`). */
  shapeGraph: string;
  [key: string]: unknown;
}

/**
 * Build the JSON string stored on `ShepardTemplate.body`. Preserves any
 * pre-existing JSON keys in `priorBody` (so non-shape DSL fields the template
 * already carried survive a visual edit).
 */
export function buildTemplateBody(
  state: ShapeEditorState,
  compiledTurtle: string,
  priorBody?: string | null,
): string {
  let base: Record<string, unknown> = {};
  if (priorBody) {
    try {
      const parsed = JSON.parse(priorBody) as unknown;
      if (parsed && typeof parsed === "object") base = { ...(parsed as Record<string, unknown>) };
    } catch {
      // prior body wasn't JSON — start fresh; the visual editor owns it now.
    }
  }
  base.editorState = state;
  base.shapeGraph = compiledTurtle;
  return JSON.stringify(base, null, 2);
}

/**
 * Reopen a stored body back into editor state. Returns null when the body has
 * no `editorState` key (i.e. it was authored as raw JSON / Turtle, not by this
 * editor) — the dialog then offers only the raw-JSON fallback.
 */
export function editorStateFromTemplateBody(body: string | null | undefined): ShapeEditorState | null {
  if (!body) return null;
  try {
    const parsed = JSON.parse(body) as unknown;
    if (
      parsed &&
      typeof parsed === "object" &&
      "editorState" in parsed &&
      (parsed as { editorState: unknown }).editorState &&
      typeof (parsed as { editorState: unknown }).editorState === "object"
    ) {
      const es = (parsed as { editorState: ShapeEditorState }).editorState;
      // Defensive normalisation — guarantee the array + flags exist.
      return {
        shapeIri: es.shapeIri ?? "",
        targetClass: es.targetClass ?? "",
        closed: !!es.closed,
        properties: Array.isArray(es.properties) ? es.properties : [],
      };
    }
  } catch {
    return null;
  }
  return null;
}
