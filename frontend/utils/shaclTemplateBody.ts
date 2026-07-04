/**
 * SHAPES-V-PREFILL-1 — pure helper for extracting an optional embedded
 * SHACL shape graph from a `ShepardTemplateIO.body` string.
 *
 * Today the template `body` is the JSON DSL described in
 * `aidocs/54 §7`. Some future SHACL-bearing templates may carry a
 * `shapeGraph` field containing Turtle directly; when present we
 * return it so the validator pre-fills its shape-graph textarea.
 *
 * Returns null when:
 *   - the body string is null/empty
 *   - the body isn't valid JSON
 *   - the JSON has no `shapeGraph` string property
 *
 * Sub-row tracker: SHAPES-V-PREFILL-3-EXTRACT-SHACL (full extraction
 * pipeline, including pulling SHACL via a referenced FileReference, is
 * out of scope here).
 */
export function extractShapeGraphFromTemplateBody(
  body: string | null | undefined,
): string | null {
  if (!body) return null;
  try {
    const parsed = JSON.parse(body) as unknown;
    if (
      parsed &&
      typeof parsed === "object" &&
      "shapeGraph" in parsed &&
      typeof (parsed as { shapeGraph: unknown }).shapeGraph === "string"
    ) {
      const sg = (parsed as { shapeGraph: string }).shapeGraph;
      return sg.length > 0 ? sg : null;
    }
  } catch {
    return null;
  }
  return null;
}
