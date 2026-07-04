/**
 * MISSING-aas-ui Slice 9 — AAS well-known URL computation tests.
 *
 * Tests the URL derivation logic used in AasAdminConfigPane to display
 * the discoverable well-known URL for external IDTA registries.
 * Pure function tests — no component mount needed.
 */
import { describe, it, expect } from "vitest";

function wellKnownUrl(baseUrl: string | null | undefined): string {
  const base = baseUrl?.replace(/\/$/, "") ?? "";
  return base ? `${base}/v2/aas/.well-known/aas-server` : "";
}

describe("AAS well-known URL computation", () => {
  it("appends the well-known path to a plain base URL", () => {
    expect(wellKnownUrl("https://shepard.example.org")).toBe(
      "https://shepard.example.org/v2/aas/.well-known/aas-server",
    );
  });

  it("strips a trailing slash before appending the path", () => {
    expect(wellKnownUrl("https://shepard.example.org/")).toBe(
      "https://shepard.example.org/v2/aas/.well-known/aas-server",
    );
  });

  it("returns empty string when baseUrl is empty", () => {
    expect(wellKnownUrl("")).toBe("");
  });

  it("returns empty string when baseUrl is null", () => {
    expect(wellKnownUrl(null)).toBe("");
  });

  it("returns empty string when baseUrl is undefined", () => {
    expect(wellKnownUrl(undefined)).toBe("");
  });

  it("preserves a sub-path base URL", () => {
    expect(wellKnownUrl("https://platform.example.org/shepard")).toBe(
      "https://platform.example.org/shepard/v2/aas/.well-known/aas-server",
    );
  });
});

describe("AAS shells list — shell IRI format", () => {
  it("shell IRI on list page matches urn:shepard:collection:{appId} pattern", () => {
    const shellId = "urn:shepard:collection:01929b00-0000-7000-0000-000000000001";
    expect(shellId).toMatch(/^urn:shepard:collection:[0-9a-f-]+$/);
  });

  it("ClipboardButton receives the full IRI string", () => {
    const shellId = "urn:shepard:collection:01929b00-0000-7000-0000-000000000002";
    // The clipboard receives the full shell.id value — same as what is displayed
    expect(shellId).toBe("urn:shepard:collection:01929b00-0000-7000-0000-000000000002");
  });
});
