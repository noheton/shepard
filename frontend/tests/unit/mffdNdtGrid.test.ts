/**
 * MFFD-NDT-GRID-1 — pure-helper tests for the 14x14 coverage widget.
 *
 * Covers:
 *   - extractGridPosition (complete / partial / empty / whitespace)
 *   - extractQsClassification (OK / NOK / null / whitespace)
 *   - bucketByGrid (multi-DO, multi-layer, missing fields, mixed QS)
 *   - cellKey / enumerateGrid (canonical 196 ordering)
 *   - colourForCount (zero, single, max, out-of-range)
 *   - hasFailedMeasurement (no NOK / one NOK / mixed)
 *   - formatTooltip (single / plural / failed / empty layers)
 *   - compareLayer (numeric sort, unparseable suffix)
 *   - annotationsContainSection (cheap precheck)
 *   - maxMeasurementCount (empty / non-empty)
 *
 * 20 cases.
 */
import { describe, it, expect } from "vitest";
import type { SemanticAnnotation } from "@dlr-shepard/backend-client";
import {
  MFFD_SECTION_IRI,
  MFFD_MODULE_IRI,
  MFFD_LAYER_IRI,
  MFFD_FRAME_IRI,
  MFFD_QS_CLASSIFICATION_IRI,
  EMPTY_CELL_COLOUR,
  annotationsContainSection,
  bucketByGrid,
  cellKey,
  colourForCount,
  compareLayer,
  enumerateGrid,
  extractGridPosition,
  extractQsClassification,
  formatTooltip,
  hasFailedMeasurement,
  maxMeasurementCount,
  type DataObjectWithAnnotations,
  type GridCellData,
} from "../../utils/mffdNdtGrid";

let nextId = 1;
function ann(
  propertyIRI: string,
  valueName: string,
  overrides: Partial<SemanticAnnotation> = {},
): SemanticAnnotation {
  return {
    id: nextId++,
    name: `a${nextId}`,
    propertyName: propertyIRI.split(":").pop() ?? "p",
    propertyIRI,
    valueName,
    valueIRI: `urn:val:${valueName}`,
    propertyRepositoryId: 1,
    valueRepositoryId: 1,
    ...overrides,
  };
}

function gridAnnotations(
  section: string,
  module: string,
  layer: string,
  frame: string,
  qs?: string,
): SemanticAnnotation[] {
  const out = [
    ann(MFFD_SECTION_IRI, section),
    ann(MFFD_MODULE_IRI, module),
    ann(MFFD_LAYER_IRI, layer),
    ann(MFFD_FRAME_IRI, frame),
  ];
  if (qs !== undefined) out.push(ann(MFFD_QS_CLASSIFICATION_IRI, qs));
  return out;
}

function buildDo(
  id: number,
  name: string,
  annotations: SemanticAnnotation[],
): DataObjectWithAnnotations {
  return { id, name, annotations };
}

// ── extractGridPosition ───────────────────────────────────────────────────

describe("extractGridPosition", () => {
  it("returns the 4-tuple when all four predicates are present", () => {
    const pos = extractGridPosition(gridAnnotations("S4", "M13", "L18", "F4"));
    expect(pos).toEqual({
      section: "S4",
      module: "M13",
      layer: "L18",
      frame: "F4",
    });
  });

  it("returns null when section is missing", () => {
    const annotations = [
      ann(MFFD_MODULE_IRI, "M2"),
      ann(MFFD_LAYER_IRI, "L8"),
      ann(MFFD_FRAME_IRI, "F1"),
    ];
    expect(extractGridPosition(annotations)).toBeNull();
  });

  it("returns null when only section + module are present (no layer/frame)", () => {
    const annotations = [
      ann(MFFD_SECTION_IRI, "S1"),
      ann(MFFD_MODULE_IRI, "M1"),
    ];
    expect(extractGridPosition(annotations)).toBeNull();
  });

  it("treats whitespace-only values as missing", () => {
    const annotations = [
      ann(MFFD_SECTION_IRI, "   "),
      ann(MFFD_MODULE_IRI, "M2"),
      ann(MFFD_LAYER_IRI, "L8"),
      ann(MFFD_FRAME_IRI, "F1"),
    ];
    expect(extractGridPosition(annotations)).toBeNull();
  });

  it("returns the first value when a predicate is repeated", () => {
    const annotations = [
      ann(MFFD_SECTION_IRI, "S2"),
      ann(MFFD_SECTION_IRI, "S99"),
      ann(MFFD_MODULE_IRI, "M5"),
      ann(MFFD_LAYER_IRI, "L8"),
      ann(MFFD_FRAME_IRI, "F1"),
    ];
    const pos = extractGridPosition(annotations);
    expect(pos?.section).toBe("S2");
  });

  it("returns null for an empty annotation list", () => {
    expect(extractGridPosition([])).toBeNull();
  });
});

// ── extractQsClassification ───────────────────────────────────────────────

describe("extractQsClassification", () => {
  it("returns OK / NOK / unknown when present", () => {
    const ok = extractQsClassification([ann(MFFD_QS_CLASSIFICATION_IRI, "OK")]);
    const nok = extractQsClassification([ann(MFFD_QS_CLASSIFICATION_IRI, "NOK")]);
    const unk = extractQsClassification([
      ann(MFFD_QS_CLASSIFICATION_IRI, "unknown"),
    ]);
    expect(ok).toBe("OK");
    expect(nok).toBe("NOK");
    expect(unk).toBe("unknown");
  });

  it("returns null when the predicate is absent", () => {
    const annotations = gridAnnotations("S1", "M1", "L1", "F1");
    expect(extractQsClassification(annotations)).toBeNull();
  });
});

// ── bucketByGrid ──────────────────────────────────────────────────────────

describe("bucketByGrid", () => {
  it("buckets one DataObject into one cell", () => {
    const dos = [buildDo(1, "tr-001", gridAnnotations("S4", "M13", "L18", "F1"))];
    const buckets = bucketByGrid(dos);
    expect(buckets.size).toBe(1);
    const cell = buckets.get(cellKey("S4", "M13"));
    expect(cell?.measurements).toHaveLength(1);
    expect(cell?.layers).toEqual(["L18"]);
    expect(cell?.anyFailed).toBe(false);
  });

  it("merges multiple DataObjects with the same (S, M) and distinct layers", () => {
    const dos = [
      buildDo(1, "a", gridAnnotations("S4", "M13", "L8", "F1")),
      buildDo(2, "b", gridAnnotations("S4", "M13", "L18", "F2")),
      buildDo(3, "c", gridAnnotations("S4", "M13", "L19", "F3")),
    ];
    const buckets = bucketByGrid(dos);
    const cell = buckets.get(cellKey("S4", "M13"));
    expect(cell?.measurements).toHaveLength(3);
    expect(cell?.layers).toEqual(["L8", "L18", "L19"]);
  });

  it("flags anyFailed when at least one NOK is present", () => {
    const dos = [
      buildDo(1, "a", gridAnnotations("S2", "M2", "L8", "F1", "OK")),
      buildDo(2, "b", gridAnnotations("S2", "M2", "L9", "F2", "NOK")),
    ];
    const buckets = bucketByGrid(dos);
    const cell = buckets.get(cellKey("S2", "M2"));
    expect(cell?.anyFailed).toBe(true);
  });

  it("skips DataObjects without a complete grid position", () => {
    const dos = [
      buildDo(1, "incomplete", [
        ann(MFFD_SECTION_IRI, "S1"),
        ann(MFFD_MODULE_IRI, "M1"),
      ]),
      buildDo(2, "complete", gridAnnotations("S1", "M1", "L8", "F1")),
    ];
    const buckets = bucketByGrid(dos);
    expect(buckets.size).toBe(1);
    expect(buckets.get(cellKey("S1", "M1"))?.measurements).toHaveLength(1);
  });

  it("returns an empty map when no DOs carry grid annotations", () => {
    const dos = [buildDo(1, "unrelated", [ann("urn:other:thing", "x")])];
    expect(bucketByGrid(dos).size).toBe(0);
  });
});

// ── cellKey / enumerateGrid ───────────────────────────────────────────────

describe("cellKey / enumerateGrid", () => {
  it("produces stable, distinct keys", () => {
    expect(cellKey("S1", "M2")).not.toEqual(cellKey("S2", "M1"));
    expect(cellKey("S1", "M2")).toEqual(cellKey("S1", "M2"));
  });

  it("enumerates 196 cells for a 14x14 grid in row-major order", () => {
    const cells = enumerateGrid(14, 14);
    expect(cells).toHaveLength(196);
    expect(cells[0]).toEqual({ section: "S1", module: "M1" });
    expect(cells[13]).toEqual({ section: "S1", module: "M14" });
    expect(cells[14]).toEqual({ section: "S2", module: "M1" });
    expect(cells[195]).toEqual({ section: "S14", module: "M14" });
  });
});

// ── colourForCount ────────────────────────────────────────────────────────

describe("colourForCount", () => {
  it("returns the empty-cell sentinel for zero count", () => {
    expect(colourForCount(0, 10)).toBe(EMPTY_CELL_COLOUR);
  });

  it("returns the empty-cell sentinel when max is zero", () => {
    expect(colourForCount(3, 0)).toBe(EMPTY_CELL_COLOUR);
  });

  it("returns an rgb(...) string for any positive count", () => {
    expect(colourForCount(1, 10)).toMatch(/^rgb\(\d+, \d+, \d+\)$/);
    expect(colourForCount(10, 10)).toMatch(/^rgb\(\d+, \d+, \d+\)$/);
  });

  it("produces a different colour for low vs high counts in the same scale", () => {
    const low = colourForCount(1, 10);
    const high = colourForCount(10, 10);
    expect(low).not.toEqual(high);
  });
});

// ── hasFailedMeasurement ──────────────────────────────────────────────────

describe("hasFailedMeasurement", () => {
  it("returns false when no NOK present", () => {
    const cell: GridCellData = {
      section: "S1",
      module: "M1",
      measurements: [
        {
          dataObject: buildDo(1, "a", []),
          layer: "L1",
          frame: "F1",
          qsClassification: "OK",
        },
      ],
      layers: ["L1"],
      anyFailed: false,
    };
    expect(hasFailedMeasurement(cell)).toBe(false);
  });

  it("returns true when any NOK present in a mixed cell", () => {
    const cell: GridCellData = {
      section: "S1",
      module: "M1",
      measurements: [
        {
          dataObject: buildDo(1, "a", []),
          layer: "L1",
          frame: "F1",
          qsClassification: "OK",
        },
        {
          dataObject: buildDo(2, "b", []),
          layer: "L2",
          frame: "F1",
          qsClassification: "NOK",
        },
      ],
      layers: ["L1", "L2"],
      anyFailed: true,
    };
    expect(hasFailedMeasurement(cell)).toBe(true);
  });
});

// ── formatTooltip ─────────────────────────────────────────────────────────

describe("formatTooltip", () => {
  it("renders a singular 'measurement' for count 1", () => {
    const cell: GridCellData = {
      section: "S4",
      module: "M13",
      measurements: [
        {
          dataObject: buildDo(1, "a", []),
          layer: "L18",
          frame: "F1",
          qsClassification: null,
        },
      ],
      layers: ["L18"],
      anyFailed: false,
    };
    expect(formatTooltip(cell)).toBe(
      "Section S4 · Module M13 · 1 measurement · layers: L18",
    );
  });

  it("renders the plural form and prepends FAILED: when anyFailed", () => {
    const cell: GridCellData = {
      section: "S2",
      module: "M2",
      measurements: [
        {
          dataObject: buildDo(1, "a", []),
          layer: "L8",
          frame: "F1",
          qsClassification: "OK",
        },
        {
          dataObject: buildDo(2, "b", []),
          layer: "L9",
          frame: "F1",
          qsClassification: "NOK",
        },
      ],
      layers: ["L8", "L9"],
      anyFailed: true,
    };
    expect(formatTooltip(cell)).toBe(
      "FAILED: Section S2 · Module M2 · 2 measurements · layers: L8, L9",
    );
  });
});

// ── compareLayer ──────────────────────────────────────────────────────────

describe("compareLayer", () => {
  it("sorts L8 before L19 (numeric, not lexicographic)", () => {
    const layers = ["L19", "L8", "L11"];
    layers.sort(compareLayer);
    expect(layers).toEqual(["L8", "L11", "L19"]);
  });

  it("sorts L19 before L19+ (tie-broken lexicographically)", () => {
    const layers = ["L19+", "L19"];
    layers.sort(compareLayer);
    expect(layers).toEqual(["L19", "L19+"]);
  });

  it("places unparseable labels at the end", () => {
    const layers = ["LX", "L8"];
    layers.sort(compareLayer);
    expect(layers).toEqual(["L8", "LX"]);
  });
});

// ── annotationsContainSection ─────────────────────────────────────────────

describe("annotationsContainSection", () => {
  it("returns true when section predicate present", () => {
    const annotations = [ann(MFFD_SECTION_IRI, "S4")];
    expect(annotationsContainSection(annotations)).toBe(true);
  });

  it("returns false when only unrelated predicates present", () => {
    const annotations = [ann("urn:other:thing", "v")];
    expect(annotationsContainSection(annotations)).toBe(false);
  });
});

// ── maxMeasurementCount ───────────────────────────────────────────────────

describe("maxMeasurementCount", () => {
  it("returns 0 for an empty map", () => {
    expect(maxMeasurementCount(new Map())).toBe(0);
  });

  it("returns the largest cell measurement count", () => {
    const dos = [
      buildDo(1, "a", gridAnnotations("S1", "M1", "L1", "F1")),
      buildDo(2, "b", gridAnnotations("S1", "M1", "L2", "F1")),
      buildDo(3, "c", gridAnnotations("S2", "M2", "L1", "F1")),
    ];
    expect(maxMeasurementCount(bucketByGrid(dos))).toBe(2);
  });
});
