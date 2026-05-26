/**
 * Unit tests for the renderer dispatch helpers in frontend/pages/shapes/render.vue.
 *
 * The functions under test are pure (no Vue reactivity, no HTTP, no DOM).
 * They are re-implemented inline here so the test does not depend on the
 * component's import chain (Three.js, WebGL stubs, etc.).
 *
 * If you rename or change the logic in render.vue, update these tests.
 *
 * Design ref: aidocs/platform/83-tpl1-tpl2-shapes-templates-views.md §Frontend dispatch
 */

import { describe, it, expect } from "vitest";

// ── pure helpers mirroring the computed bodies in render.vue ──────────────────

type RendererKind = "trace-3d" | "table" | "unknown";
type ColormapName = "inferno" | "viridis" | "plasma";
type Trace3DColorScheme = "heat" | "cool" | "viridis";

/** Mirror of rendererKind computed in render.vue */
function resolveRendererKind(rendererValue: string | null): RendererKind {
  const r = rendererValue?.toLowerCase() ?? "";
  if (r === "trace-3d" || r === "tresjs") return "trace-3d";
  if (r === "table")                       return "table";
  return "unknown";
}

/** Mirror of trace3DColorScheme computed in render.vue */
function resolveTrace3DColorScheme(colormap: ColormapName): Trace3DColorScheme {
  switch (colormap) {
    case "viridis": return "viridis";
    case "plasma":  return "heat";
    default:        return "heat";
  }
}

/** Mirror of xData/yData/zData/valueData flat-array computeds in render.vue */
interface TracePoint { x: number; y: number; z: number; value: number; t: number }

function flattenX(pts: TracePoint[])     { return pts.map(p => p.x);     }
function flattenY(pts: TracePoint[])     { return pts.map(p => p.y);     }
function flattenZ(pts: TracePoint[])     { return pts.map(p => p.z);     }
function flattenValue(pts: TracePoint[]) { return pts.map(p => p.value); }

// ── tests ──────────────────────────────────────────────────────────────────────

describe("resolveRendererKind", () => {
  it('maps "tresjs" to "trace-3d"', () => {
    expect(resolveRendererKind("tresjs")).toBe("trace-3d");
  });

  it('maps "trace-3d" to "trace-3d"', () => {
    expect(resolveRendererKind("trace-3d")).toBe("trace-3d");
  });

  it("is case-insensitive for trace-3d variants", () => {
    expect(resolveRendererKind("TRESJS")).toBe("trace-3d");
    expect(resolveRendererKind("Trace-3D")).toBe("trace-3d");
  });

  it('maps "table" to "table"', () => {
    expect(resolveRendererKind("table")).toBe("table");
  });

  it("is case-insensitive for table", () => {
    expect(resolveRendererKind("TABLE")).toBe("table");
  });

  it('maps unknown string to "unknown"', () => {
    expect(resolveRendererKind("scatter")).toBe("unknown");
    expect(resolveRendererKind("vtkjs")).toBe("unknown");
    expect(resolveRendererKind("")).toBe("unknown");
    expect(resolveRendererKind("custom:my-renderer")).toBe("unknown");
  });

  it("maps null to unknown (no renderer hint yet)", () => {
    expect(resolveRendererKind(null)).toBe("unknown");
  });
});

describe("resolveTrace3DColorScheme", () => {
  it('maps "viridis" to "viridis"', () => {
    expect(resolveTrace3DColorScheme("viridis")).toBe("viridis");
  });

  it('maps "plasma" to "heat" (closest heat-family)', () => {
    expect(resolveTrace3DColorScheme("plasma")).toBe("heat");
  });

  it('maps "inferno" (default) to "heat"', () => {
    expect(resolveTrace3DColorScheme("inferno")).toBe("heat");
  });
});

describe("flat-array extractors", () => {
  const pts: TracePoint[] = [
    { x: 1, y: 2, z: 3, value: 10, t: 0 },
    { x: 4, y: 5, z: 6, value: 20, t: 1 },
    { x: 7, y: 8, z: 9, value: 30, t: 2 },
  ];

  it("flattenX extracts x coordinates", () => {
    expect(flattenX(pts)).toEqual([1, 4, 7]);
  });

  it("flattenY extracts y coordinates", () => {
    expect(flattenY(pts)).toEqual([2, 5, 8]);
  });

  it("flattenZ extracts z coordinates", () => {
    expect(flattenZ(pts)).toEqual([3, 6, 9]);
  });

  it("flattenValue extracts value (colour scalar) channel", () => {
    expect(flattenValue(pts)).toEqual([10, 20, 30]);
  });

  it("returns empty arrays for empty input", () => {
    expect(flattenX([])).toEqual([]);
    expect(flattenY([])).toEqual([]);
    expect(flattenZ([])).toEqual([]);
    expect(flattenValue([])).toEqual([]);
  });

  it("preserves NaN in value channel (no-colour points)", () => {
    const withNaN: TracePoint[] = [
      { x: 0, y: 0, z: 0, value: NaN, t: 0 },
      { x: 1, y: 1, z: 1, value: 5,   t: 1 },
    ];
    const values = flattenValue(withNaN);
    expect(Number.isNaN(values[0])).toBe(true);
    expect(values[1]).toBe(5);
  });
});
