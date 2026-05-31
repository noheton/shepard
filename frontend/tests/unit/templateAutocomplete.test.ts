/**
 * UI-SHAPES-RENDER-PICKERS-001 (Q) — unit tests for TemplateAutocomplete
 * pure helpers. Confirms the picker hits /v2/templates with the right
 * query, and renders options in the "Name — appId…" shape the user sees.
 */
import { describe, it, expect } from "vitest";
import {
  formatOption,
  templatesUrl,
} from "~/utils/templateAutocomplete";

describe("templatesUrl", () => {
  it("appends ?kind= when a kind filter is supplied", () => {
    expect(templatesUrl("https://x", "VIEW_RECIPE")).toBe(
      "https://x/v2/templates?kind=VIEW_RECIPE",
    );
  });

  it("omits the query when kind is '*'", () => {
    expect(templatesUrl("https://x", "*")).toBe("https://x/v2/templates");
  });

  it("omits the query when kind is empty", () => {
    expect(templatesUrl("https://x", "")).toBe("https://x/v2/templates");
  });

  it("URL-encodes a kind containing special chars", () => {
    expect(templatesUrl("https://x", "WEIRD KIND")).toBe(
      "https://x/v2/templates?kind=WEIRD%20KIND",
    );
  });
});

describe("formatOption", () => {
  const sample = {
    appId: "019e7243-f995-7914-be80-53e367aa5172",
    name: "LUMEN Trace3D",
    templateKind: "VIEW_RECIPE",
    description: "Three-axis tracer for hotfire campaigns",
  };

  it("formats title as 'Name — appId-prefix…'", () => {
    const opt = formatOption(sample, "VIEW_RECIPE");
    expect(opt.title).toBe("LUMEN Trace3D — 019e7243…");
    expect(opt.value).toBe("019e7243-f995-7914-be80-53e367aa5172");
    expect(opt.subtitle).toBe("Three-axis tracer for hotfire campaigns");
  });

  it("does NOT append the kind badge when filtering by a specific kind", () => {
    const opt = formatOption(sample, "VIEW_RECIPE");
    expect(opt.title).not.toContain("[VIEW_RECIPE]");
  });

  it("appends the kind badge when kind filter is '*'", () => {
    const opt = formatOption(sample, "*");
    expect(opt.title).toContain("[VIEW_RECIPE]");
  });

  it("uses '(unnamed)' when the template has no name", () => {
    const opt = formatOption({ ...sample, name: undefined }, "VIEW_RECIPE");
    expect(opt.title).toContain("(unnamed)");
  });

  it("leaves subtitle undefined when description is missing", () => {
    const opt = formatOption(
      { ...sample, description: undefined },
      "VIEW_RECIPE",
    );
    expect(opt.subtitle).toBeUndefined();
  });
});
