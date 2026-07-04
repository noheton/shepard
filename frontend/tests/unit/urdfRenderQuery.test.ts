/**
 * V2-SWEEP Wave 2 — /shapes/render URDF bootstrap query contract.
 *
 * Canonical shape carries the FileReference appId only
 * (`?renderer=urdf&urdfFileAppId=…`); the raw `urdfUrl`/`packagePath`
 * params are a deprecated legacy fallback (UI-PATHS-FROM-REFERENCES).
 */
import { describe, it, expect } from "vitest";
import type { LocationQuery } from "vue-router";
import {
  parseUrdfRenderQuery,
  isUrdfRenderQuery,
} from "~/utils/urdfRenderQuery";

const APP_ID = "019e0000-0000-7000-8000-00000000beef";

describe("urdfRenderQuery — V2-SWEEP Wave 2", () => {
  it("prefers the FileReference appId and ignores any legacy URL params", () => {
    const q = {
      renderer: "urdf",
      urdfFileAppId: APP_ID,
      urdfUrl: "https://evil.example/leak.urdf",
      packagePath: "/mnt/leak",
    } as LocationQuery;
    const parsed = parseUrdfRenderQuery(q);
    expect(parsed.fileReferenceAppId).toBe(APP_ID);
    expect(parsed.legacyUrl).toBeUndefined();
    expect(parsed.legacyPackagePath).toBeUndefined();
  });

  it("falls back to the deprecated legacy URL shape when no appId is given", () => {
    const q = {
      renderer: "urdf",
      urdfUrl: encodeURIComponent("/urdf-samples/two-link-arm.urdf"),
      packagePath: encodeURIComponent("/meshes"),
    } as LocationQuery;
    const parsed = parseUrdfRenderQuery(q);
    expect(parsed.fileReferenceAppId).toBeUndefined();
    expect(parsed.legacyUrl).toBe("/urdf-samples/two-link-arm.urdf");
    expect(parsed.legacyPackagePath).toBe("/meshes");
  });

  it("defaults to the bundled sample when neither appId nor legacy URL is present", () => {
    const parsed = parseUrdfRenderQuery({ renderer: "urdf" } as LocationQuery);
    expect(parsed.legacyUrl).toBe("/urdf-samples/two-link-arm.urdf");
    expect(parsed.legacyPackagePath).toBe("");
  });

  it("recognises the URDF renderer from renderer=urdf, urdfFileAppId, or legacy urdfUrl", () => {
    expect(isUrdfRenderQuery({ renderer: "urdf" } as LocationQuery)).toBe(true);
    expect(
      isUrdfRenderQuery({ urdfFileAppId: APP_ID } as LocationQuery),
    ).toBe(true);
    expect(
      isUrdfRenderQuery({ urdfUrl: "x.urdf" } as LocationQuery),
    ).toBe(true);
    // Trace3D bootstrap (roles present) must not be hijacked.
    expect(
      isUrdfRenderQuery({ urdfUrl: "x.urdf", roles: "abc" } as LocationQuery),
    ).toBe(false);
    expect(isUrdfRenderQuery({} as LocationQuery)).toBe(false);
  });
});
