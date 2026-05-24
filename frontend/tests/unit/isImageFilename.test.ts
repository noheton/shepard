import { describe, it, expect } from "vitest";
import { isImageFilename } from "../../composables/container/useFetchFileThumbnail";

describe("isImageFilename (UI-009)", () => {
  it("returns false for nullish or empty input", () => {
    expect(isImageFilename(undefined)).toBe(false);
    expect(isImageFilename(null)).toBe(false);
    expect(isImageFilename("")).toBe(false);
  });

  it("returns true for common image extensions (case-insensitive)", () => {
    expect(isImageFilename("foo.png")).toBe(true);
    expect(isImageFilename("FOO.PNG")).toBe(true);
    expect(isImageFilename("photo.JPG")).toBe(true);
    expect(isImageFilename("scan.jpeg")).toBe(true);
    expect(isImageFilename("frame.webp")).toBe(true);
    expect(isImageFilename("vector.svg")).toBe(true);
    expect(isImageFilename("medical.tiff")).toBe(true);
    expect(isImageFilename("Apple.heic")).toBe(true);
  });

  it("returns false for non-image extensions (the 404 noise case)", () => {
    // These are the file types that currently fire `thumbnail?size=64` → 404.
    expect(isImageFilename("notes.txt")).toBe(false);
    expect(isImageFilename("design.rdk")).toBe(false);
    expect(isImageFilename("report.pdf")).toBe(false);
    expect(isImageFilename("data.csv")).toBe(false);
    expect(isImageFilename("model.step")).toBe(false);
    expect(isImageFilename("archive.zip")).toBe(false);
  });

  it("returns false for filenames without an extension", () => {
    expect(isImageFilename("README")).toBe(false);
    expect(isImageFilename("Makefile")).toBe(false);
    expect(isImageFilename("dotfile.")).toBe(false);
  });

  it("uses only the final extension on multi-dot filenames", () => {
    expect(isImageFilename("archive.tar.gz")).toBe(false);
    expect(isImageFilename("photo.backup.jpg")).toBe(true);
  });
});
