/**
 * UX612-M1 — unit tests for the /shapes/render error mapper.
 *
 * The endpoint answers 422 with a raw JSON body when the template's
 * `templateKind != VIEW_RECIPE`; pre-fix the page dumped
 * `422 : {"error":"render not yet supported for templateKind=…"}` into the
 * alert. The mapper translates it into a human sentence.
 */
import { describe, it, expect } from "vitest";
import { mapRenderFetchError } from "~/utils/shapesRenderErrors";

describe("mapRenderFetchError (UX612-M1)", () => {
  it("maps the 422 templateKind mismatch to a human sentence naming the kind", () => {
    const message = mapRenderFetchError(422, "Unprocessable Entity", {
      error:
        "render not yet supported for templateKind=DATAOBJECT_RECIPE; only " +
        "VIEW_RECIPE is supported in this release. Use GET /v2/templates?kind=view " +
        "to discover VIEW_RECIPE templates.",
    });

    expect(message).toContain("DATAOBJECT_RECIPE");
    expect(message).toContain("only VIEW_RECIPE templates can be rendered");
    // No raw JSON leaks into the user-facing sentence.
    expect(message).not.toContain("{");
    expect(message).not.toContain("422");
  });

  it("maps a 422 without a parseable kind to a generic human sentence", () => {
    const message = mapRenderFetchError(422, "Unprocessable Entity", {
      error: "nope",
    });
    expect(message).toContain("not a VIEW_RECIPE template");
    expect(message).not.toContain("{");
  });

  it("maps a 404 to a stale-template hint", () => {
    const message = mapRenderFetchError(404, "Not Found", {
      error: "template not found: abc",
    });
    expect(message).toContain("Template not found");
  });

  it("keeps the diagnostic status + body shape for other errors", () => {
    const message = mapRenderFetchError(500, "Internal Server Error", {
      error: "boom",
    });
    expect(message).toContain("500");
    expect(message).toContain("boom");
  });
});
