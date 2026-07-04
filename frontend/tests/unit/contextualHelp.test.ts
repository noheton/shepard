/**
 * Contextual help resolution — unit tests for the pure `helpPageForPath`
 * (`composables/useContextualHelp.ts`): route → doc-page-key mapping,
 * most-specific-first precedence, the /help self-hide, and the index fallback.
 *
 * Pure-helper-pattern tests; no component mount (mirrors actionMenu.test.ts —
 * the Vuetify/Nuxt mount chain is covered by Playwright per repo convention).
 */
import { describe, it, expect } from "vitest";

import { helpPageForPath } from "~/composables/useContextualHelp";

describe("helpPageForPath", () => {
  it("hides on the help page itself", () => {
    expect(helpPageForPath("/help")).toBeNull();
    expect(helpPageForPath("/help?page=reference/collections")).toBeNull();
  });

  it("maps DataObject detail more specifically than the collection list", () => {
    expect(helpPageForPath("/collections/abc/dataobjects/xyz")).toBe(
      "reference/data-objects",
    );
    expect(helpPageForPath("/collections/abc")).toBe("reference/collections");
    expect(helpPageForPath("/collections")).toBe("reference/collections");
  });

  it("maps each container kind to its reference doc", () => {
    expect(helpPageForPath("/containers/timeseries/1")).toBe(
      "reference/timeseries-reference",
    );
    expect(helpPageForPath("/containers/file/1")).toBe("reference/file-reference");
    expect(helpPageForPath("/containers/video/1")).toBe(
      "reference/video-stream-references",
    );
    expect(helpPageForPath("/containers")).toBe("reference/containers");
  });

  it("maps semantic + tools + admin + profile surfaces", () => {
    expect(helpPageForPath("/semantic/sparql")).toBe("reference/semantic-repositories");
    expect(helpPageForPath("/semantic/vocabularies")).toBe(
      "reference/semantic-annotations",
    );
    expect(helpPageForPath("/tools")).toBe("reference/view-recipes");
    expect(helpPageForPath("/shapes/render")).toBe("reference/view-recipes");
    expect(helpPageForPath("/admin")).toBe("admin");
    expect(helpPageForPath("/me")).toBe("reference/user-profile");
  });

  it("maps the home page and falls back to the index for unknowns", () => {
    expect(helpPageForPath("/")).toBe("getting-started");
    expect(helpPageForPath("/something-unknown")).toBe("index");
  });

  it("ignores trailing slash, query and hash", () => {
    expect(helpPageForPath("/collections/")).toBe("reference/collections");
    expect(helpPageForPath("/admin/?tab=users")).toBe("admin");
    expect(helpPageForPath("/projects#section")).toBe("reference/projects");
  });
});
