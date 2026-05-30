/**
 * DMP-DOWNLOAD-NAV-01 — unit tests for the DownloadDmpButton helpers.
 *
 * Mirrors the inline-helper test pattern used by EditFileReferenceDialog
 * and RunKrlPreviewButton. Tests the pure URL builder + filename
 * sanitiser without mounting Vue.
 */
import { describe, it, expect } from "vitest";
import {
  dmpSnippetUrl,
  dmpFilenameFor,
} from "../../components/context/collection/dmpDownloadHelpers";

describe("dmpDownloadHelpers — dmpSnippetUrl", () => {
  it("builds the canonical FAIR7 endpoint URL", () => {
    expect(
      dmpSnippetUrl(
        "https://shepard-api.nuclide.systems",
        "019e7243-f995-7914-be80-53e367aa5172",
      ),
    ).toBe(
      "https://shepard-api.nuclide.systems/v2/collections/019e7243-f995-7914-be80-53e367aa5172/dmp-snippet",
    );
  });

  it("URL-encodes the appId so a stray slash never breaks the path", () => {
    expect(dmpSnippetUrl("https://x", "bad/id")).toBe(
      "https://x/v2/collections/bad%2Fid/dmp-snippet",
    );
  });

  it("does not double-slash when given a trimmed base URL", () => {
    expect(dmpSnippetUrl("https://x", "abc")).toBe(
      "https://x/v2/collections/abc/dmp-snippet",
    );
  });
});

describe("dmpDownloadHelpers — dmpFilenameFor", () => {
  it("uses a sanitised collection name when present", () => {
    expect(dmpFilenameFor("LUMEN Hotfire Campaign 2026", "appid-xyz")).toBe(
      "LUMEN-Hotfire-Campaign-2026-dmp-snippet.md",
    );
  });

  it("collapses runs of replacement characters", () => {
    expect(dmpFilenameFor("foo // bar //  baz", "appid-xyz")).toBe(
      "foo-bar-baz-dmp-snippet.md",
    );
  });

  it("strips leading and trailing dashes", () => {
    expect(dmpFilenameFor("///weird///", "appid-xyz")).toBe(
      "weird-dmp-snippet.md",
    );
  });

  it("preserves dots and underscores (filesystem-safe)", () => {
    expect(dmpFilenameFor("v1.2_release", "appid-xyz")).toBe(
      "v1.2_release-dmp-snippet.md",
    );
  });

  it("falls back to a short appId stem on blank / whitespace name", () => {
    expect(dmpFilenameFor("", "019e7243-f995-7914-be80-53e367aa5172")).toBe(
      "collection-019e7243-dmp-snippet.md",
    );
    expect(dmpFilenameFor(null, "019e7243-f995-7914-be80-53e367aa5172")).toBe(
      "collection-019e7243-dmp-snippet.md",
    );
    expect(
      dmpFilenameFor("   ", "019e7243-f995-7914-be80-53e367aa5172"),
    ).toBe("collection-019e7243-dmp-snippet.md");
  });

  it("falls back when the sanitised name collapses to empty", () => {
    expect(dmpFilenameFor("///", "019e7243-f995-7914-be80-53e367aa5172")).toBe(
      "collection-019e7243-dmp-snippet.md",
    );
  });
});
