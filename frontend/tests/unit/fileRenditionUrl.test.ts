import { describe, it, expect } from "vitest";
import {
  isTiffFilename,
  buildInlineImageContentUrl,
} from "../../utils/fileRenditionUrl";

describe("isTiffFilename (TIFF-PREVIEW-SUPPORT)", () => {
  it("returns false for nullish or empty input", () => {
    expect(isTiffFilename(undefined)).toBe(false);
    expect(isTiffFilename(null)).toBe(false);
    expect(isTiffFilename("")).toBe(false);
  });

  it("returns true for .tif and .tiff, case-insensitively", () => {
    expect(isTiffFilename("tps_eval_0001.tif")).toBe(true);
    expect(isTiffFilename("tps_eval_0001.tiff")).toBe(true);
    expect(isTiffFilename("FRAME.TIF")).toBe(true);
    expect(isTiffFilename("Frame.TIFF")).toBe(true);
  });

  it("returns false for other image extensions", () => {
    expect(isTiffFilename("photo.png")).toBe(false);
    expect(isTiffFilename("photo.jpg")).toBe(false);
    expect(isTiffFilename("photo.jpeg")).toBe(false);
    expect(isTiffFilename("photo.webp")).toBe(false);
    expect(isTiffFilename("photo.gif")).toBe(false);
    expect(isTiffFilename("photo.bmp")).toBe(false);
  });

  it("returns false for non-image extensions", () => {
    expect(isTiffFilename("notes.txt")).toBe(false);
    expect(isTiffFilename("report.pdf")).toBe(false);
  });
});

describe("buildInlineImageContentUrl (TIFF-PREVIEW-SUPPORT)", () => {
  const base = "https://shepard.example/v2/references/abc-123/content";

  it("appends ?rendition=png for a .tif filename", () => {
    expect(buildInlineImageContentUrl(base, "frame.tif")).toBe(
      `${base}?rendition=png`,
    );
  });

  it("appends ?rendition=png for a .tiff filename", () => {
    expect(buildInlineImageContentUrl(base, "tps_eval_0001.tiff")).toBe(
      `${base}?rendition=png`,
    );
  });

  it("appends &rendition=png when the URL already has a query string", () => {
    const withQuery = `${base}?access_token=xyz`;
    expect(buildInlineImageContentUrl(withQuery, "frame.tiff")).toBe(
      `${withQuery}&rendition=png`,
    );
  });

  it("leaves the URL unchanged for browser-renderable formats", () => {
    expect(buildInlineImageContentUrl(base, "photo.png")).toBe(base);
    expect(buildInlineImageContentUrl(base, "photo.jpg")).toBe(base);
  });

  it("leaves the URL unchanged when filename is null/undefined", () => {
    expect(buildInlineImageContentUrl(base, null)).toBe(base);
    expect(buildInlineImageContentUrl(base, undefined)).toBe(base);
  });
});
