/**
 * KRL-INTERPRETER-06 — unit tests for the RunKrlPreviewButton visibility
 * predicate. Follows the same inline-helper pattern as
 * EditFileReferenceDialog.test.ts.
 */
import { describe, it, expect } from "vitest";
import { isKrlSrcFile } from "../../components/container/file/runKrlPreviewButtonHelpers";

describe("RunKrlPreviewButton — isKrlSrcFile", () => {
  it("returns true for plain .src files", () => {
    expect(isKrlSrcFile("Ply_5_layup.src")).toBe(true);
  });

  it("is case-insensitive on the extension", () => {
    expect(isKrlSrcFile("PROGRAM.SRC")).toBe(true);
    expect(isKrlSrcFile("program.SrC")).toBe(true);
  });

  it("returns false for unrelated extensions", () => {
    expect(isKrlSrcFile("Ply_5_layup.dat")).toBe(false);
    expect(isKrlSrcFile("model.urdf")).toBe(false);
    expect(isKrlSrcFile("README.md")).toBe(false);
  });

  it("returns false for an undefined / empty name", () => {
    expect(isKrlSrcFile(undefined)).toBe(false);
    expect(isKrlSrcFile(null)).toBe(false);
    expect(isKrlSrcFile("")).toBe(false);
  });
});
