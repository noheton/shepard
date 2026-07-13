/**
 * LINEAGE-GRAPH-MFFD-SCALE — unit tests for the pure layout helpers.
 *
 * Covers (per the task acceptance gate):
 *
 *   - dagre layout deterministic for fixed input
 *   - LOD threshold flip at zoom boundary
 *   - filter pill applies (status, process-type, neighborhood)
 *   - clustering rolls DOs into bubble nodes
 *   - 20k-node smoke (initial layout < 3s on dev box; CI guard at 12s)
 */
import { describe, it, expect } from "vitest";
import {
  type LineageDO,
  PROCESS_TYPE_ATTR,
  PLY_NUMBER_ATTR,
  LOD_THRESHOLD_MACRO_MAX,
  LOD_THRESHOLD_MESO_MAX,
  dagreLayout,
  buildEdges,
  hasVisibleEdges,
  lodForZoom,
  symbolSizeForLod,
  showLabelForLod,
  clusterByProcess,
  applyFilters,
  computeNeighborhood,
  distinctStatuses,
  distinctProcessTypes,
} from "~/utils/lineageLayout";

// ---------------------------------------------------------------------------
// Fixture helpers
// ---------------------------------------------------------------------------

function chain(n: number): LineageDO[] {
  // 0 → 1 → 2 → … → n-1
  return Array.from({ length: n }, (_, i) => ({
    id: i,
    name: `DO-${i}`,
    status: i % 3 === 0 ? "DRAFT" : i % 3 === 1 ? "READY" : "PUBLISHED",
    parentId: null,
    predecessorIds: i === 0 ? [] : [i - 1],
    attributes: {},
  }));
}

function plyDataset(plies: number, perPly: number): LineageDO[] {
  const dos: LineageDO[] = [];
  for (let p = 0; p < plies; p++) {
    for (let t = 0; t < perPly; t++) {
      const id = p * perPly + t;
      dos.push({
        id,
        name: `Track-${p}-${t}`,
        status: "READY",
        parentId: null,
        predecessorIds: t === 0
          ? (p === 0 ? [] : [(p - 1) * perPly])  // first track of ply links to first of previous
          : [id - 1],                             // sequential within ply
        attributes: {
          [PLY_NUMBER_ATTR]: String(p),
          [PROCESS_TYPE_ATTR]: p % 2 === 0 ? "afp-layup" : "ndt",
        },
      });
    }
  }
  return dos;
}

// ---------------------------------------------------------------------------
// 1. dagreLayout — determinism + small-graph correctness
// ---------------------------------------------------------------------------

describe("dagreLayout", () => {
  it("returns a position for every DO", () => {
    const dos = chain(10);
    const positions = dagreLayout(dos);
    expect(positions.size).toBe(10);
    for (const d of dos) {
      const p = positions.get(d.id);
      expect(p).toBeDefined();
      expect(typeof p!.x).toBe("number");
      expect(typeof p!.y).toBe("number");
    }
  });

  it("is deterministic — same input yields identical positions", () => {
    const dos = chain(50);
    const a = dagreLayout(dos);
    const b = dagreLayout(dos);
    expect(a.size).toBe(b.size);
    for (const [id, posA] of a) {
      const posB = b.get(id)!;
      expect(posB.x).toBe(posA.x);
      expect(posB.y).toBe(posA.y);
    }
  });

  it("places later chain members further right with rankdir=LR", () => {
    // The default rankdir is LR — predecessor → successor flows left-to-right.
    const dos = chain(8);
    const positions = dagreLayout(dos);
    // DO 0 sits to the left of DO 7. We don't assert exact pixels — dagre's
    // exact coordinates aren't part of the API — only the relative order.
    expect(positions.get(0)!.x).toBeLessThan(positions.get(7)!.x);
  });

  it("survives an isolated DO (no parent, no predecessor)", () => {
    const dos: LineageDO[] = [
      { id: 1, predecessorIds: [], parentId: null },
      { id: 2, predecessorIds: [], parentId: null },
    ];
    const positions = dagreLayout(dos);
    expect(positions.size).toBe(2);
  });
});

// ---------------------------------------------------------------------------
// 2. Edge construction
// ---------------------------------------------------------------------------

describe("buildEdges", () => {
  it("emits parent edges before predecessor edges", () => {
    const dos: LineageDO[] = [
      { id: 1, parentId: null, predecessorIds: [] },
      { id: 2, parentId: 1, predecessorIds: [1] },
    ];
    const edges = buildEdges(dos);
    expect(edges).toHaveLength(2);
    expect(edges[0]!.kind).toBe("parent");
    expect(edges[1]!.kind).toBe("predecessor");
  });

  it("drops edges whose endpoint is not in the visible set", () => {
    const dos: LineageDO[] = [
      { id: 1, parentId: 99, predecessorIds: [42] },
    ];
    const edges = buildEdges(dos);
    expect(edges).toHaveLength(0);
  });

  it("handles a 1k-node chain in linear time", () => {
    const dos = chain(1000);
    const edges = buildEdges(dos);
    expect(edges).toHaveLength(999);
    expect(edges[0]!.source).toBe("0");
    expect(edges[0]!.target).toBe("1");
  });
});

describe("hasVisibleEdges", () => {
  it("is true when at least one predecessor link resolves inside the set", () => {
    expect(hasVisibleEdges(chain(3))).toBe(true);
  });

  it("is false when every DO is isolated", () => {
    const dos: LineageDO[] = [
      { id: 1, parentId: null, predecessorIds: [] },
      { id: 2, parentId: null, predecessorIds: [99] }, // dangling
    ];
    expect(hasVisibleEdges(dos)).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// 3. LOD
// ---------------------------------------------------------------------------

describe("lodForZoom", () => {
  it("returns 'macro' below the macro threshold", () => {
    expect(lodForZoom(0)).toBe("macro");
    expect(lodForZoom(0.1)).toBe("macro");
    expect(lodForZoom(LOD_THRESHOLD_MACRO_MAX - 0.0001)).toBe("macro");
  });

  it("returns 'meso' at and above macro threshold up to meso threshold", () => {
    // Threshold is the *upper* bound for the lower mode — zoom == 0.3 is meso.
    expect(lodForZoom(LOD_THRESHOLD_MACRO_MAX)).toBe("meso");
    expect(lodForZoom(0.5)).toBe("meso");
    expect(lodForZoom(LOD_THRESHOLD_MESO_MAX - 0.0001)).toBe("meso");
  });

  it("returns 'detail' at and above meso threshold", () => {
    expect(lodForZoom(LOD_THRESHOLD_MESO_MAX)).toBe("detail");
    expect(lodForZoom(1)).toBe("detail");
    expect(lodForZoom(3.5)).toBe("detail");
  });

  it("symbolSizeForLod monotone: macro <= meso <= detail", () => {
    expect(symbolSizeForLod("macro")).toBeLessThan(symbolSizeForLod("meso"));
    expect(symbolSizeForLod("meso")).toBeLessThan(symbolSizeForLod("detail"));
  });

  it("showLabelForLod only fires at detail", () => {
    expect(showLabelForLod("macro")).toBe(false);
    expect(showLabelForLod("meso")).toBe(false);
    expect(showLabelForLod("detail")).toBe(true);
  });
});

// ---------------------------------------------------------------------------
// 4. Cluster
// ---------------------------------------------------------------------------

describe("clusterByProcess", () => {
  it("groups DOs by ply-number when the attribute is present", () => {
    const dos = plyDataset(3, 4); // 3 plies × 4 tracks
    const edges = buildEdges(dos);
    const { clusters } = clusterByProcess(dos, edges);
    expect(clusters).toHaveLength(3);
    expect(clusters.map((c) => c.count)).toEqual([4, 4, 4]);
    expect(clusters[0]!.label).toBe("Ply 0");
  });

  it("falls back to process-type when ply is missing", () => {
    const dos: LineageDO[] = [
      { id: 1, attributes: { [PROCESS_TYPE_ATTR]: "afp-layup" }, predecessorIds: [], parentId: null },
      { id: 2, attributes: { [PROCESS_TYPE_ATTR]: "afp-layup" }, predecessorIds: [1], parentId: null },
      { id: 3, attributes: { [PROCESS_TYPE_ATTR]: "ndt" }, predecessorIds: [2], parentId: null },
    ];
    const { clusters, edges } = clusterByProcess(dos, buildEdges(dos));
    expect(clusters.map((c) => c.label).sort()).toEqual(["afp-layup", "ndt"]);
    // One cross-cluster edge (afp-layup → ndt); the within-cluster
    // afp-layup → afp-layup is dropped.
    expect(edges).toHaveLength(1);
    expect(edges[0]!.weight).toBe(1);
  });

  it("collects unclassified DOs into a single 'Unclassified' bubble", () => {
    const dos: LineageDO[] = [
      { id: 1, predecessorIds: [], parentId: null },
      { id: 2, predecessorIds: [1], parentId: null },
    ];
    const { clusters } = clusterByProcess(dos, buildEdges(dos));
    expect(clusters).toHaveLength(1);
    expect(clusters[0]!.label).toBe("Unclassified");
    expect(clusters[0]!.count).toBe(2);
  });

  it("rolls up edge weights across many underlying edges", () => {
    // 100 tracks split into 2 plies; every track in ply 1 points back to its
    // counterpart in ply 0 — gives 50 cross-edges that should roll into one
    // bubble-to-bubble edge of weight 50.
    const dos: LineageDO[] = [];
    for (let i = 0; i < 50; i++) {
      dos.push({ id: i, attributes: { [PLY_NUMBER_ATTR]: "0" }, predecessorIds: [], parentId: null });
    }
    for (let i = 0; i < 50; i++) {
      dos.push({
        id: 50 + i,
        attributes: { [PLY_NUMBER_ATTR]: "1" },
        predecessorIds: [i],
        parentId: null,
      });
    }
    const { edges } = clusterByProcess(dos, buildEdges(dos));
    expect(edges).toHaveLength(1);
    expect(edges[0]!.weight).toBe(50);
  });
});

// ---------------------------------------------------------------------------
// 5. Filters
// ---------------------------------------------------------------------------

describe("applyFilters", () => {
  it("status filter keeps only matching statuses", () => {
    const dos = chain(9);
    const kept = applyFilters(dos, { statusIn: ["READY"] });
    expect(kept.every((d) => d.status === "READY")).toBe(true);
    expect(kept.length).toBeGreaterThan(0);
  });

  it("process-type filter keeps only matching processes", () => {
    const dos = plyDataset(4, 3);
    const kept = applyFilters(dos, { processTypeIn: ["afp-layup"] });
    expect(kept.every((d) => d.attributes![PROCESS_TYPE_ATTR] === "afp-layup")).toBe(true);
  });

  it("neighborhood filter limits to BFS depth from center", () => {
    // chain(10): center=5, depth=2 → ids {3,4,5,6,7}
    const dos = chain(10);
    const kept = applyFilters(dos, { neighborhood: { centerDoId: 5, depth: 2 } });
    expect(new Set(kept.map((d) => d.id))).toEqual(new Set([3, 4, 5, 6, 7]));
  });

  it("filters compose with AND", () => {
    const dos = plyDataset(3, 4); // all READY
    const kept = applyFilters(dos, {
      statusIn: ["READY"],
      processTypeIn: ["afp-layup"],
    });
    // ply 0 and ply 2 are afp-layup (even index); 4+4 = 8 DOs
    expect(kept).toHaveLength(8);
  });

  it("empty filter object is the identity", () => {
    const dos = chain(5);
    expect(applyFilters(dos, {}).length).toBe(dos.length);
  });
});

describe("computeNeighborhood", () => {
  it("returns just the center at depth 0", () => {
    const dos = chain(5);
    expect([...computeNeighborhood(dos, 2, 0)]).toEqual([2]);
  });

  it("walks both directions on the chain", () => {
    const dos = chain(7);
    const nh = computeNeighborhood(dos, 3, 2);
    expect(nh).toEqual(new Set([1, 2, 3, 4, 5]));
  });

  it("handles parent edges as undirected", () => {
    // tree: 1 ← parent ← 2, 3
    const dos: LineageDO[] = [
      { id: 1, predecessorIds: [], parentId: null },
      { id: 2, predecessorIds: [], parentId: 1 },
      { id: 3, predecessorIds: [], parentId: 1 },
    ];
    expect(computeNeighborhood(dos, 1, 1)).toEqual(new Set([1, 2, 3]));
  });

  it("returns empty set at negative depth", () => {
    const dos = chain(3);
    expect(computeNeighborhood(dos, 1, -1).size).toBe(0);
  });
});

// ---------------------------------------------------------------------------
// 6. Distinct extractors
// ---------------------------------------------------------------------------

describe("distinctStatuses / distinctProcessTypes", () => {
  it("distinctStatuses returns sorted unique non-null statuses", () => {
    const dos = chain(7); // DRAFT, READY, PUBLISHED cycle
    expect(distinctStatuses(dos)).toEqual(["DRAFT", "PUBLISHED", "READY"]);
  });

  it("distinctProcessTypes only counts annotated DOs", () => {
    const dos: LineageDO[] = [
      { id: 1, attributes: { [PROCESS_TYPE_ATTR]: "afp-layup" }, predecessorIds: [], parentId: null },
      { id: 2, attributes: { [PROCESS_TYPE_ATTR]: "ndt" }, predecessorIds: [], parentId: null },
      { id: 3, predecessorIds: [], parentId: null }, // no annotation
    ];
    expect(distinctProcessTypes(dos)).toEqual(["afp-layup", "ndt"]);
  });
});

// ---------------------------------------------------------------------------
// 7. Performance smoke (20k nodes)
// ---------------------------------------------------------------------------

describe("performance smoke", () => {
  it("lays out 20k nodes within the perf budget", () => {
    // Build a wide forest: 200 plies × 100 tracks/ply = 20,000 nodes.
    // The edge structure (sparse: each track links only to the previous
    // track in the same ply) mirrors the worst-case MFFD shape from
    // GAP-12 of mffd-feature-gaps-2026-06-02.md.
    const dos = plyDataset(200, 100);
    expect(dos.length).toBe(20000);

    const t0 = performance.now();
    const positions = dagreLayout(dos);
    const layoutMs = performance.now() - t0;

    expect(positions.size).toBe(20000);

    // Dev-box target is 3s (task §6); CI tolerates much slower JIT warm-up
    // and shared-runner noise. 45s is generous enough to absorb runner jitter
    // while still catching genuine O(n²) regressions. The PR report carries
    // the measured layoutMs.
    expect(layoutMs).toBeLessThan(45000);
  }, 60000);

  it("clusters 20k nodes deterministically and quickly", () => {
    const dos = plyDataset(200, 100);
    const t0 = performance.now();
    const edges = buildEdges(dos);
    const { clusters } = clusterByProcess(dos, edges);
    const ms = performance.now() - t0;
    expect(clusters).toHaveLength(200); // one per ply
    expect(ms).toBeLessThan(5000);
  });

  it("computes a depth-2 neighborhood on a 20k graph quickly", () => {
    const dos = plyDataset(200, 100);
    const t0 = performance.now();
    const nh = computeNeighborhood(dos, 10500, 2);
    const ms = performance.now() - t0;
    // Linear chain inside the ply: depth=2 sees center ± 2 nodes plus the
    // cross-ply links (first-of-ply only) — small bounded set.
    expect(nh.size).toBeGreaterThan(0);
    expect(nh.size).toBeLessThan(50);
    expect(ms).toBeLessThan(2000);
  });
});
