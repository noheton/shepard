/**
 * UX612-M1 — map POST /v2/shapes/render fetch failures to human-readable
 * sentences instead of dumping raw status + JSON into the alert.
 *
 * The 422 case is the contract mismatch the audit flagged: the endpoint
 * accepts only `templateKind=VIEW_RECIPE` and answers e.g.
 * `{"error":"render not yet supported for templateKind=DATAOBJECT_RECIPE; …"}`
 * — a wall of JSON that blames the user. Translate it.
 */

/** Extract the offending templateKind from the server's 422 error string. */
function extractTemplateKind(body: unknown): string | null {
  const message =
    typeof body === "object" && body !== null && "error" in body
      ? String((body as { error: unknown }).error)
      : typeof body === "string"
        ? body
        : "";
  const match = message.match(/templateKind=([A-Za-z_]+)/);
  return match?.[1] ?? null;
}

export function mapRenderFetchError(
  status: number,
  statusText: string,
  body: unknown,
): string {
  if (status === 422) {
    const kind = extractTemplateKind(body);
    const kindClause = kind
      ? `This template is a ${kind} template`
      : "This template is not a VIEW_RECIPE template";
    return (
      `${kindClause} — only VIEW_RECIPE templates can be rendered. ` +
      "Pick a VIEW_RECIPE template (the template autocomplete above only lists those)."
    );
  }
  if (status === 404) {
    return "Template not found — it may have been deleted or the appId is stale.";
  }
  return `${status} ${statusText}: ${JSON.stringify(body)}`;
}
