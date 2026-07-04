/**
 * TOOLS-CONTEXT-VOCAB-BACKEND-1 — unit tests for the vocab browser URL builder.
 */
import { describe, it, expect } from "vitest";

import { buildVocabBrowserUrl } from "../../utils/vocabBrowserUrl";

const BASE = "https://shepard.example.org";

describe("buildVocabBrowserUrl", () => {
  it("falls back to the full inventory endpoint when usedByAppId is null", () => {
    expect(buildVocabBrowserUrl(BASE, null, null)).toBe(
      "https://shepard.example.org/v2/semantic/vocabularies",
    );
  });

  it("falls back to the full inventory endpoint when usedByAppId is empty string", () => {
    expect(buildVocabBrowserUrl(BASE, "", "collection")).toBe(
      "https://shepard.example.org/v2/semantic/vocabularies",
    );
  });

  it("builds the used-by endpoint with scope=collection when scope is the literal collection", () => {
    const appId = "0197b6a2-7b4c-7000-8a3b-1234567890ab";
    expect(buildVocabBrowserUrl(BASE, appId, "collection")).toBe(
      `https://shepard.example.org/v2/semantic/vocabularies/used-by/${appId}?scope=collection`,
    );
  });

  it("normalises any other scope value to data-object", () => {
    const appId = "do-1";
    expect(buildVocabBrowserUrl(BASE, appId, null)).toBe(
      "https://shepard.example.org/v2/semantic/vocabularies/used-by/do-1?scope=data-object",
    );
    expect(buildVocabBrowserUrl(BASE, appId, "")).toBe(
      "https://shepard.example.org/v2/semantic/vocabularies/used-by/do-1?scope=data-object",
    );
    expect(buildVocabBrowserUrl(BASE, appId, "Collection")).toBe(
      // case-sensitive — "Collection" is not the literal "collection"
      "https://shepard.example.org/v2/semantic/vocabularies/used-by/do-1?scope=data-object",
    );
  });

  it("URI-encodes special characters in the appId path segment", () => {
    expect(buildVocabBrowserUrl(BASE, "weird id/with?chars", "collection")).toBe(
      "https://shepard.example.org/v2/semantic/vocabularies/used-by/weird%20id%2Fwith%3Fchars?scope=collection",
    );
  });

  it("strips a trailing slash from the v2 base", () => {
    expect(buildVocabBrowserUrl(BASE + "/", null, null)).toBe(
      "https://shepard.example.org/v2/semantic/vocabularies",
    );
  });
});
