/**
 * LINEAGE-GRAPH-MFFD-SCALE — pure helpers for CollectionLineageGraph at scale.
 *
 * Extracted from CollectionLineageGraph.vue (GAP-12, task #25) so the logic
 * can be unit-tested without mounting Vuetify or ECharts. The .vue file keeps
 * orchestration only — these functions own:
 *
 *   1. dagreLayout()       — layered DAG positions (LR, ranksep tuned for MFFD)
 *   2. buildEdges()        — parent + predecessor edges with line style
 *   3. lodForZoom()        — Level-Of-Detail thresholds for symbolSize + labels
 *   4. clusterByProcess()  — macro-mode collapse to process-type / ply bubbles
 *   5. applyFilters()      — client-side filter pills (status / process / depth)
 *   6. computeNeighborhood() — depth-limited BFS for the "Around N" filter
 *
 * Why no ECharts dependency here: keeping these pure makes the perf smoke
 * test ("20k nodes layout in < 3s") deterministic and lets us tune LOD
 * thresholds with confidence.
 */
import dagre from "@dagrejs/dagre";

// ---------------------------------------------------------------------------
// Shapes
// ---------------------------------------------------------------------------

/**
 * Minimal DataObject shape this module needs. Matches DataObjectListItemV2
 * but kept structurally so tests can use plain object literals.
 */
export interface LineageDO {
  id: number;
  name?: string;
  status?: string | null;
  description?: string | null;
  parentId?: number | null;
  predecessorIds?: number[];
  attributes?: Record<string, string>;
}

export interface NodePosition { x: number; y: number }

export interface LineageEdge {
  source: string;
  target: string;
  /** "parent" | "predecessor" — used downstream for line styling */
  kind: "parent" | "predecessor";
}

export interface LineageGraphData {
  nodes: LineageDO[];
  edges: LineageEdge[];
  positions: Map<number, NodePosition>;
}

// ---------------------------------------------------------------------------
// 1. Dagre layout
// ---------------------------------------------------------------------------

/**
 * Layout configuration for the dagre engine. LR direction matches a process
 * chain visualisation (left = oldest, right = newest). Defaults tuned by
 * trial-and-error on the LUMEN seed and the synthetic MFFD seed.
 */
export interface DagreConfig {
  rankdir?: "LR" | "TB" | "RL" | "BT";
  nodesep?: number;
  ranksep?: number;
  marginx?: number;
  marginy?: number;
  nodeWidth?: number;
  nodeHeight?: number;
}

/**
 * Compute deterministic node positions for a layered DAG. Identical inputs
 * (same set of DOs in same iteration order, same config) yield identical
 * output — important so the perf smoke test isn't flaky.
 */
export function dagreLayout(
  dos: LineageDO[],
  config: DagreConfig = {},
): Map<number, NodePosition> {
  const g = new dagre.graphlib.Graph();
  g.setGraph({
    rankdir: config.rankdir ?? "LR",
    nodesep: config.nodesep ?? 55,
    ranksep: config.ranksep ?? 200,
    marginx: config.marginx ?? 60,
    marginy: config.marginy ?? 40,
  });
  g.setDefaultEdgeLabel(() => ({}));

  const visibleIds = new Set(dos.map((d) => d.id));
  const w = config.nodeWidth ?? 90;
  const h = config.nodeHeight ?? 30;

  for (const d of dos) {
    g.setNode(String(d.id), { width: w, height: h });
  }
  for (const d of dos) {
    for (const predId of d.predecessorIds ?? []) {
      if (visibleIds.has(predId)) {
        g.setEdge(String(predId), String(d.id));
      }
    }
    const parentId = d.parentId ?? null;
    if (parentId != null && visibleIds.has(parentId)) {
      g.setEdge(String(parentId), String(d.id));
    }
  }

  dagre.layout(g);

  const positions = new Map<number, NodePosition>();
  for (const d of dos) {
    const node = g.node(String(d.id));
    positions.set(d.id, { x: node.x, y: node.y });
  }
  return positions;
}

// ---------------------------------------------------------------------------
// 2. Edge construction
// ---------------------------------------------------------------------------

/**
 * Build the edge list for a visible-DO set. Edges whose endpoints are both
 * inside the visible set get included; everything else drops silently.
 *
 * Order matters for ECharts series consistency: emit parent edges first,
 * predecessor edges second, so legend ordering stays predictable.
 */
export function buildEdges(dos: LineageDO[]): LineageEdge[] {
  const visibleIds = new Set(dos.map((d) => d.id));
  const edges: LineageEdge[] = [];

  for (const d of dos) {
    const parentId = d.parentId ?? null;
    if (parentId != null && visibleIds.has(parentId)) {
      edges.push({ source: String(parentId), target: String(d.id), kind: "parent" });
    }
  }
  for (const d of dos) {
    for (const predId of d.predecessorIds ?? []) {
      if (visibleIds.has(predId)) {
        edges.push({ source: String(predId), target: String(d.id), kind: "predecessor" });
      }
    }
  }
  return edges;
}

/**
 * True when at least one DO inside the visible set carries a connection
 * (parent or predecessor) to another DO inside the same set.
 */
export function hasVisibleEdges(dos: LineageDO[]): boolean {
  const visibleIds = new Set(dos.map((d) => d.id));
  return dos.some((d) => {
    const preds = d.predecessorIds ?? [];
    if (preds.some((id) => visibleIds.has(id))) return true;
    const parentId = d.parentId ?? null;
    return parentId != null && visibleIds.has(parentId);
  });
}

// ---------------------------------------------------------------------------
// 3. Level-Of-Detail
// ---------------------------------------------------------------------------

/**
 * Three LOD modes keyed on the ECharts zoom factor:
 *
 *   - "macro"  — zoom < 0.3 — collapse to process-type / ply clusters
 *   - "meso"   — 0.3 ≤ zoom < 0.8 — individual nodes, no labels, smaller markers
 *   - "detail" — zoom ≥ 0.8 — full labels + chips + ref-kind icons
 *
 * The thresholds are deliberately exposed as named exports so the Playwright
 * regression and the Vitest cases can assert them without redefining magic
 * numbers.
 */
export type LodMode = "macro" | "meso" | "detail";

export const LOD_THRESHOLD_MACRO_MAX = 0.3;
export const LOD_THRESHOLD_MESO_MAX = 0.8;

export function lodForZoom(zoom: number): LodMode {
  if (zoom < LOD_THRESHOLD_MACRO_MAX) return "macro";
  if (zoom < LOD_THRESHOLD_MESO_MAX) return "meso";
  return "detail";
}

/** Visual size of a node by LOD mode. Larger = visible from further out. */
export function symbolSizeForLod(mode: LodMode): number {
  switch (mode) {
    case "macro":  return 14;
    case "meso":   return 18;
    case "detail": return 22;
  }
}

/** Whether to render label text under each node. Macro/meso hide labels. */
export function showLabelForLod(mode: LodMode): boolean {
  return mode === "detail";
}

// ---------------------------------------------------------------------------
// 4. Macro-mode clustering
// ---------------------------------------------------------------------------

/**
 * A cluster bubble that represents many underlying DOs at macro zoom.
 * `count` drives bubble symbolSize so the eye can compare density;
 * `edgeCount` drives bubble-to-bubble edge thickness in macro mode.
 */
export interface LineageCluster {
  /** Stable bubble id; prefixed `cluster:` so it cannot collide with DO ids. */
  id: string;
  /** Human-readable label — used as the bubble tooltip and macro chip. */
  label: string;
  /** Number of DOs that fold into this bubble. */
  count: number;
  /** DO ids inside this bubble — for click-through to a filtered list. */
  doIds: number[];
}

/**
 * Returned by clusterByProcess: the bubbles + the bubble-to-bubble edges.
 */
export interface ClusterGraph {
  clusters: LineageCluster[];
  edges: Array<{ source: string; target: string; weight: number }>;
}

/**
 * The annotation namespace the MFFD seed (V100) writes onto each DO.
 * Exposed so tests + tooltips can reference it without retyping the string.
 */
export const PROCESS_TYPE_ATTR = "urn:shepard:mffd:process-type";
export const PLY_NUMBER_ATTR = "urn:shepard:mffd:ply-number";

/**
 * Macro-mode collapse. Each DO maps to a key derived from:
 *
 *   1. `attributes["urn:shepard:mffd:ply-number"]` (preferred at MFFD scale —
 *      the ply axis is the natural cluster grain for 8k+ tracks)
 *   2. else `attributes["urn:shepard:mffd:process-type"]` (LUMEN + cross-MFFD)
 *   3. else the literal "unclassified"
 *
 * Edges between two DOs in different clusters become a bubble-to-bubble
 * edge with weight = count of underlying edges. Self-edges (within-cluster)
 * are dropped — they don't add information at this LOD.
 */
export function clusterByProcess(
  dos: LineageDO[],
  edges: LineageEdge[],
): ClusterGraph {
  const keyFor = (d: LineageDO): string => {
    const ply = d.attributes?.[PLY_NUMBER_ATTR];
    if (ply) return `ply:${ply}`;
    const proc = d.attributes?.[PROCESS_TYPE_ATTR];
    if (proc) return `proc:${proc}`;
    return "unclassified";
  };

  // Group DOs by key. Map iteration order is insertion order — keeps the
  // tests deterministic regardless of input shuffling.
  const buckets = new Map<string, LineageDO[]>();
  for (const d of dos) {
    const k = keyFor(d);
    const arr = buckets.get(k) ?? [];
    arr.push(d);
    buckets.set(k, arr);
  }

  const clusters: LineageCluster[] = [];
  const labelFor = (k: string): string => {
    if (k === "unclassified") return "Unclassified";
    if (k.startsWith("ply:")) return `Ply ${k.slice(4)}`;
    if (k.startsWith("proc:")) return k.slice(5);
    return k;
  };

  for (const [k, arr] of buckets) {
    clusters.push({
      id: `cluster:${k}`,
      label: labelFor(k),
      count: arr.length,
      doIds: arr.map((d) => d.id),
    });
  }

  // Build a lookup: DO id → cluster id
  const doToCluster = new Map<number, string>();
  for (const c of clusters) {
    for (const id of c.doIds) doToCluster.set(id, c.id);
  }

  // Roll edges into bubble-to-bubble counts; drop within-cluster.
  const edgeCounts = new Map<string, number>();
  for (const e of edges) {
    const srcId = Number(e.source);
    const tgtId = Number(e.target);
    const srcCluster = doToCluster.get(srcId);
    const tgtCluster = doToCluster.get(tgtId);
    if (!srcCluster || !tgtCluster) continue;
    if (srcCluster === tgtCluster) continue;
    const key = `${srcCluster}→${tgtCluster}`;
    edgeCounts.set(key, (edgeCounts.get(key) ?? 0) + 1);
  }

  const clusterEdges: Array<{ source: string; target: string; weight: number }> = [];
  for (const [key, weight] of edgeCounts) {
    const parts = key.split("→");
    clusterEdges.push({ source: parts[0] ?? "", target: parts[1] ?? "", weight });
  }

  return { clusters, edges: clusterEdges };
}

// ---------------------------------------------------------------------------
// 5. Filter pills
// ---------------------------------------------------------------------------

/**
 * Filter state applied above the graph. All filters compose with AND.
 *
 * - statusIn: when non-empty, keep DOs whose `.status` is in this set.
 * - processTypeIn: when non-empty, keep DOs whose
 *     attributes["urn:shepard:mffd:process-type"] is in this set.
 * - neighborhood: when set, keep DOs within `depth` hops of `centerDoId`
 *     (undirected — both predecessor and successor walks).
 */
export interface LineageFilter {
  statusIn?: string[];
  processTypeIn?: string[];
  neighborhood?: { centerDoId: number; depth: number };
}

export function applyFilters(
  dos: LineageDO[],
  filter: LineageFilter,
): LineageDO[] {
  let pool = dos;

  if (filter.statusIn && filter.statusIn.length > 0) {
    const allowed = new Set(filter.statusIn);
    pool = pool.filter((d) => d.status != null && allowed.has(d.status));
  }

  if (filter.processTypeIn && filter.processTypeIn.length > 0) {
    const allowed = new Set(filter.processTypeIn);
    pool = pool.filter((d) => {
      const proc = d.attributes?.[PROCESS_TYPE_ATTR];
      return proc != null && allowed.has(proc);
    });
  }

  if (filter.neighborhood) {
    const nh = computeNeighborhood(dos, filter.neighborhood.centerDoId, filter.neighborhood.depth);
    pool = pool.filter((d) => nh.has(d.id));
  }

  return pool;
}

// ---------------------------------------------------------------------------
// 6. Neighborhood BFS
// ---------------------------------------------------------------------------

/**
 * BFS from `centerId` up to `depth` hops in either direction. Returns the
 * set of DO ids that are reachable. Reasoning over the ORIGINAL DO list
 * (not a filtered subset) so a "Around DO N, depth=2" pill behaves the
 * same regardless of other filter state.
 *
 * Direction is undirected on parent + predecessor edges combined — the
 * user thinks "show what's near this DO", not "show its descendants".
 */
export function computeNeighborhood(
  allDos: LineageDO[],
  centerId: number,
  depth: number,
): Set<number> {
  if (depth < 0) return new Set();

  // Build undirected adjacency from parent + predecessor edges
  const adj = new Map<number, Set<number>>();
  const link = (a: number, b: number) => {
    if (!adj.has(a)) adj.set(a, new Set());
    if (!adj.has(b)) adj.set(b, new Set());
    adj.get(a)!.add(b);
    adj.get(b)!.add(a);
  };
  for (const d of allDos) {
    const pid = d.parentId ?? null;
    if (pid != null) link(d.id, pid);
    for (const predId of d.predecessorIds ?? []) {
      link(d.id, predId);
    }
  }

  const visited = new Set<number>([centerId]);
  let frontier: number[] = [centerId];
  for (let step = 0; step < depth; step++) {
    const next: number[] = [];
    for (const id of frontier) {
      for (const nbr of adj.get(id) ?? []) {
        if (!visited.has(nbr)) {
          visited.add(nbr);
          next.push(nbr);
        }
      }
    }
    frontier = next;
    if (frontier.length === 0) break;
  }
  return visited;
}

// ---------------------------------------------------------------------------
// Convenience extractors
// ---------------------------------------------------------------------------

/**
 * The set of distinct `.status` values across a DO collection. Sorted
 * lexically for stable pill ordering. Null / undefined statuses dropped.
 */
export function distinctStatuses(dos: LineageDO[]): string[] {
  const s = new Set<string>();
  for (const d of dos) if (d.status) s.add(d.status);
  return [...s].sort();
}

/**
 * The set of distinct process-type values across a DO collection. Drops
 * undefined values; sorted lexically for stable pill ordering.
 */
export function distinctProcessTypes(dos: LineageDO[]): string[] {
  const s = new Set<string>();
  for (const d of dos) {
    const proc = d.attributes?.[PROCESS_TYPE_ATTR];
    if (proc) s.add(proc);
  }
  return [...s].sort();
}
