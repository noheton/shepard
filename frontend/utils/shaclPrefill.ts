/**
 * SHAPES-V-PREFILL — pure helpers for the /shapes/validate page's
 * auto-prefill flow.
 *
 * The validation page accepts two URL params from the in-context
 * launchers (TOOLS-CONTEXT-DO-SHACL):
 *
 *   ?focusAppId=<dataObjectAppId>&scope=data-object[&templateAppId=<...>]
 *
 * When `focusAppId` is set AND `scope !== "collection"` the page
 * fetches `GET /v2/data-objects/{appId}/rdf` (Turtle) and pre-fills the
 * data-graph textarea. When `templateAppId` is set the page fetches
 * the template via `GET /v2/templates/{appId}` and — if the body
 * carries a `shapeGraph` Turtle string — pre-fills the shape-graph
 * textarea.
 *
 * **Validation is never auto-run.** The user clicks Validate after
 * inspecting the pre-filled content. Auto-running would hide
 * operator-visible parse errors and confuse the "what did I just
 * submit" mental model.
 *
 * Tested via Vitest in `tests/unit/shaclPrefill.test.ts`.
 */

/**
 * Should the data-graph auto-load fire for this URL state?
 *
 * Returns `true` iff a `focusAppId` is present AND the scope is not
 * "collection" (the RDF endpoint is DataObject-scoped only; a
 * collection-focused validation page leaves the textarea editable for
 * a paste workflow).
 */
export function shouldFetchDataObjectRdf(
  focusAppId: string | null | undefined,
  focusScope: string | null | undefined,
): boolean {
  if (!focusAppId || focusAppId.length === 0) return false;
  if (focusScope === "collection") return false;
  return true;
}

/**
 * Build the `/v2/data-objects/{appId}/rdf` URL for a given v2 base
 * (`getV2Base()`) and DataObject appId. Encodes the appId.
 */
export function buildDataObjectRdfUrl(v2Base: string, appId: string): string {
  return `${v2Base}/v2/data-objects/${encodeURIComponent(appId)}/rdf`;
}
