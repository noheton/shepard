/**
 * V2CONV-B6 — the live-preview round-trip for the visual template editor.
 *
 * Two backend calls:
 *
 *   1. `POST /v2/shapes/build` (V2CONV-B6, this feature's new endpoint) —
 *      compiles the editor's JSON DSL into canonical SHACL Turtle. This is the
 *      live preview: the editor serialises its rows on every change and shows
 *      the returned `shapeGraph`.
 *   2. `POST /v2/shapes/validate` (B2, already shipped) — round-trip-validates
 *      a candidate data graph against the just-compiled shape, so the author can
 *      confirm the shape actually constrains data the way they intend before
 *      saving.
 *
 * Raw-fetch + base-URL + bearer-token, matching `pages/shapes/validate.vue` and
 * the sibling semantic composables. Debouncing is the caller's concern (the
 * editor debounces `compile` on row edits).
 *
 * Design: aidocs/platform/191-v2-surface-convergence.md §3.
 */
import { ref } from "vue";
import type { ShapeBuildRequest } from "~/utils/templateShapeDsl";

export interface ShapeBuildResult {
  shapeIri: string | null;
  shapeGraph: string | null;
  error: string | null;
}

export interface ShapeValidationFinding {
  focusNode: string | null;
  resultPath: string | null;
  value: string | null;
  severity: string;
  message: string;
}

export interface ShapeValidationReport {
  conforms: boolean;
  parseError: string | null;
  engineError: string | null;
  findings: ShapeValidationFinding[];
}

function v2Base(): string {
  const { public: publicConfig } = useRuntimeConfig();
  const explicit = (publicConfig as { backendV2ApiUrl?: string }).backendV2ApiUrl;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (publicConfig.backendApiUrl as string).replace(/\/shepard\/api\/?$/, "").replace(/\/$/, "");
}

function jsonHeaders(): Record<string, string> {
  const { data: session } = useAuth();
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    Accept: "application/json",
  };
  const token = session.value?.accessToken;
  if (token) headers["Authorization"] = `Bearer ${token}`;
  return headers;
}

export function useShapeBuilder() {
  const compiledTurtle = ref<string | null>(null);
  const compiledShapeIri = ref<string | null>(null);
  const compileError = ref<string | null>(null);
  const compiling = ref(false);

  const report = ref<ShapeValidationReport | null>(null);
  const validateError = ref<string | null>(null);
  const validating = ref(false);

  /** Compile a DSL to Turtle via POST /v2/shapes/build. Returns the result. */
  async function compile(dsl: ShapeBuildRequest): Promise<ShapeBuildResult> {
    compiling.value = true;
    compileError.value = null;
    try {
      const res = await fetch(`${v2Base()}/v2/shapes/build`, {
        method: "POST",
        headers: jsonHeaders(),
        body: JSON.stringify(dsl),
      });
      const body = (await res.json()) as ShapeBuildResult;
      if (!res.ok) {
        // 400 carries an error message in the body.error field.
        compileError.value = body?.error ?? `${res.status} ${res.statusText}`;
        compiledTurtle.value = null;
        compiledShapeIri.value = null;
        return { shapeIri: null, shapeGraph: null, error: compileError.value };
      }
      compiledTurtle.value = body.shapeGraph;
      compiledShapeIri.value = body.shapeIri;
      compileError.value = body.error ?? null;
      return body;
    } catch (e) {
      compileError.value = e instanceof Error ? e.message : String(e);
      compiledTurtle.value = null;
      compiledShapeIri.value = null;
      return { shapeIri: null, shapeGraph: null, error: compileError.value };
    } finally {
      compiling.value = false;
    }
  }

  /**
   * Validate a candidate data graph against a shape graph via
   * POST /v2/shapes/validate. The IO fields are `dataTurtle` / `shapeTurtle`
   * (see `ShapeValidationRequestIO`).
   */
  async function validate(dataTurtle: string, shapeTurtle: string): Promise<ShapeValidationReport | null> {
    validating.value = true;
    validateError.value = null;
    report.value = null;
    try {
      const res = await fetch(`${v2Base()}/v2/shapes/validate`, {
        method: "POST",
        headers: jsonHeaders(),
        body: JSON.stringify({ dataTurtle, shapeTurtle }),
      });
      const body = await res.text();
      if (!res.ok) {
        validateError.value = `${res.status} ${res.statusText}\n${body}`;
        return null;
      }
      report.value = JSON.parse(body) as ShapeValidationReport;
      return report.value;
    } catch (e) {
      validateError.value = e instanceof Error ? e.message : String(e);
      return null;
    } finally {
      validating.value = false;
    }
  }

  return {
    compiledTurtle,
    compiledShapeIri,
    compileError,
    compiling,
    report,
    validateError,
    validating,
    compile,
    validate,
  };
}
