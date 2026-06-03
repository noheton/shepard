<script setup lang="ts">
/**
 * LINEAGE-GRAPH-MFFD-SCALE (GAP-12 / task #25) — Collection lineage graph
 * sized for MFFD-scale collections (~20k node + edge count).
 *
 * Engine choice: ECharts (Canvas) + @dagrejs/dagre layered layout.
 * Rationale (see the GAP-12 row in aidocs/16 for the long form):
 *   - The previous implementation was *already* ECharts + dagre; the actual
 *     bug was the silent `NODE_CAP = 150` truncation, not the engine.
 *   - ECharts Canvas hits the 20k-node performance budget; DOM-based
 *     alternatives (vue-flow, cytoscape SVG) reportedly cap out at
 *     ~5k nodes even with viewport culling.
 *   - Keeping the engine is also the smallest possible diff, which
 *     keeps small-Collection rendering identical.
 *
 * What this rewrite adds on top of the previous component:
 *   1. NODE_CAP removed — full lineage shown.
 *   2. Level-Of-Detail via the roam zoom event:
 *        macro (<0.3): one bubble per ply / process-type
 *        meso  (<0.8): individual nodes, no labels
 *        detail (≥0.8): full labels + status colour
 *   3. Filter pills above the canvas (status / process-type / "Around N").
 *   4. Minimap (second small <v-chart> instance) — survey-only.
 *   5. Click-through to the DataObject detail page.
 *
 * All client-side; no new backend endpoints. The existing
 * `useFetchAllDataObjects` composable already paginates the DO list.
 */
import VChart from "vue-echarts";
import { use } from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import { GraphChart } from "echarts/charts";
import { TooltipComponent, LegendComponent } from "echarts/components";
import type { DataObjectListItemV2 } from "@dlr-shepard/backend-client";
import { useFetchAllDataObjects } from "~/composables/context/useFetchAllDataObjects";
import { computeLineageState, type LineageState } from "~/utils/lineageState";
import {
  STATUS_COLORS,
  nodeColor,
  truncateLabel,
  baseGraphSeriesConfig,
} from "~/composables/useLineageGraph";
import {
  type LineageDO,
  type LineageFilter,
  type LodMode,
  applyFilters,
  buildEdges,
  clusterByProcess,
  dagreLayout,
  distinctProcessTypes,
  distinctStatuses,
  hasVisibleEdges,
  lodForZoom,
  showLabelForLod,
  symbolSizeForLod,
} from "~/utils/lineageLayout";

if (import.meta.client) {
  use([CanvasRenderer, GraphChart, TooltipComponent, LegendComponent]);
}

const props = defineProps<{
  collectionId: number;
  collectionAppId?: string;
}>();

const router = useRouter();
const collectionAppIdRef = computed(() => props.collectionAppId ?? null);
const { dataObjects, loading } = useFetchAllDataObjects(props.collectionId, collectionAppIdRef);

// ---------------------------------------------------------------------------
// Type-narrowing helpers — DataObjectListItemV2 carries extra fields, but the
// pure helpers want the minimal LineageDO shape. One coerce point.
// ---------------------------------------------------------------------------

function toLineageDO(d: DataObjectListItemV2): LineageDO {
  const raw = d as unknown as {
    id: number;
    name?: string;
    status?: string | null;
    description?: string | null;
    parentId?: number | null;
    predecessorIds?: number[];
    attributes?: Record<string, string>;
  };
  return {
    id: raw.id,
    name: raw.name,
    status: raw.status,
    description: raw.description,
    parentId: raw.parentId ?? null,
    predecessorIds: raw.predecessorIds ?? [],
    attributes: raw.attributes ?? {},
  };
}

const allDos = computed<LineageDO[]>(() => dataObjects.value.map(toLineageDO));

// ---------------------------------------------------------------------------
// Filter state — kept in a single reactive object so a Reset button is one
// assignment, not a fan-out of separate refs.
// ---------------------------------------------------------------------------

const statusFilter = ref<string[]>([]);
const processTypeFilter = ref<string[]>([]);
const neighborhoodCenter = ref<number | null>(null);
const neighborhoodDepth = ref<number>(2);

const currentFilter = computed<LineageFilter>(() => {
  const f: LineageFilter = {};
  if (statusFilter.value.length > 0) f.statusIn = statusFilter.value;
  if (processTypeFilter.value.length > 0) f.processTypeIn = processTypeFilter.value;
  if (neighborhoodCenter.value != null) {
    f.neighborhood = { centerDoId: neighborhoodCenter.value, depth: neighborhoodDepth.value };
  }
  return f;
});

const anyFilterActive = computed<boolean>(() =>
  statusFilter.value.length > 0 ||
  processTypeFilter.value.length > 0 ||
  neighborhoodCenter.value != null,
);

function resetFilters(): void {
  statusFilter.value = [];
  processTypeFilter.value = [];
  neighborhoodCenter.value = null;
}

// Available filter values, derived from the full DO list (not the filtered
// view — otherwise toggling one pill makes the others disappear).
const availableStatuses = computed<string[]>(() => distinctStatuses(allDos.value));
const availableProcessTypes = computed<string[]>(() => distinctProcessTypes(allDos.value));

// ---------------------------------------------------------------------------
// Filtered DO set + edges + positions
// ---------------------------------------------------------------------------

const visibleDos = computed<LineageDO[]>(() => applyFilters(allDos.value, currentFilter.value));
const visibleEdges = computed(() => buildEdges(visibleDos.value));
const positions = computed(() => dagreLayout(visibleDos.value));

const hasEdges = computed<boolean>(() => hasVisibleEdges(visibleDos.value));
const lineageState = computed<LineageState>(() =>
  computeLineageState(loading.value, allDos.value, hasEdges.value),
);

// ---------------------------------------------------------------------------
// LOD — track zoom from the main chart's `georoam` event and recompute the
// option. ECharts emits a relative delta; we track the cumulative product.
// ---------------------------------------------------------------------------

const zoom = ref<number>(1);
const lod = computed<LodMode>(() => lodForZoom(zoom.value));

function onGraphRoam(params: { zoom?: number }): void {
  if (typeof params.zoom === "number" && params.zoom > 0) {
    zoom.value = zoom.value * params.zoom;
    // Clamp to the same range ECharts uses internally so LOD doesn't go
    // wild on a long pinch session.
    if (zoom.value < 0.05) zoom.value = 0.05;
    if (zoom.value > 20) zoom.value = 20;
  }
}

// ---------------------------------------------------------------------------
// Series builder — three branches keyed on LOD
// ---------------------------------------------------------------------------

const STATUS_COLOR_BUBBLE = "#4097CC";

function buildDetailNodes(): Array<Record<string, unknown>> {
  const dos = visibleDos.value;
  const pos = positions.value;
  const size = symbolSizeForLod(lod.value);
  const label = showLabelForLod(lod.value);
  return dos.map((d) => {
    const p = pos.get(d.id) ?? { x: 0, y: 0 };
    return {
      id: String(d.id),
      name: d.name ?? String(d.id),
      value: d.id,
      x: p.x,
      y: p.y,
      itemStyle: { color: nodeColor(d.status ?? "") },
      symbolSize: size,
      label: { show: label, fontSize: 11 },
    };
  });
}

function buildDetailEdges(): Array<Record<string, unknown>> {
  return visibleEdges.value.map((e) => ({
    source: e.source,
    target: e.target,
    lineStyle: e.kind === "predecessor"
      ? { type: "dashed", color: "#FCA54D", opacity: 0.8 }
      : { type: "solid", color: "#888", opacity: 0.5 },
    // Carry through for the tooltip
    _kind: e.kind,
  }));
}

const clusterGraph = computed(() => clusterByProcess(visibleDos.value, visibleEdges.value));

function buildMacroNodes(): Array<Record<string, unknown>> {
  // Cluster bubbles are positioned by running dagre over them too — that way
  // the macro view inherits the same left-to-right flow as the detail view.
  const { clusters, edges } = clusterGraph.value;
  if (clusters.length === 0) return [];

  // Synthesize tiny LineageDO objects so dagreLayout can reuse its existing
  // signature. id is the index; predecessor edges come from the bubble graph.
  const bubbleIndex = new Map(clusters.map((c, i) => [c.id, i] as const));
  const synth: LineageDO[] = clusters.map((c, i) => ({
    id: i,
    name: c.label,
    predecessorIds: edges
      .filter((e) => e.target === c.id)
      .map((e) => bubbleIndex.get(e.source))
      .filter((v): v is number => typeof v === "number"),
    parentId: null,
    attributes: {},
  }));
  const synthPositions = dagreLayout(synth, { ranksep: 280, nodesep: 80 });

  return clusters.map((c, i) => {
    const p = synthPositions.get(i) ?? { x: 0, y: 0 };
    // Bubble size scales with sqrt(count) so a 100x denser ply is ~10x larger
    // visually — readable at a glance without overwhelming the small ones.
    const size = Math.min(80, 18 + Math.sqrt(c.count) * 2);
    return {
      id: c.id,
      name: c.label,
      value: c.count,
      x: p.x,
      y: p.y,
      itemStyle: { color: STATUS_COLOR_BUBBLE, opacity: 0.85 },
      symbolSize: size,
      label: { show: true, fontSize: 12, formatter: `${c.label}\n${c.count}` },
    };
  });
}

function buildMacroEdges(): Array<Record<string, unknown>> {
  const { edges } = clusterGraph.value;
  return edges.map((e) => ({
    source: e.source,
    target: e.target,
    lineStyle: {
      // Edge thickness from underlying edge count — capped so a dense ply
      // pair doesn't draw a 40-px bar across the screen.
      width: Math.min(8, 1 + Math.log10(e.weight + 1) * 3),
      color: "#888",
      opacity: 0.6,
    },
  }));
}

// ---------------------------------------------------------------------------
// ECharts option for the main canvas
// ---------------------------------------------------------------------------

const chartOption = computed(() => {
  const dos = visibleDos.value;
  if (!dos.length) return {};

  const isMacro = lod.value === "macro";
  const nodes = isMacro ? buildMacroNodes() : buildDetailNodes();
  const edges = isMacro ? buildMacroEdges() : buildDetailEdges();

  return {
    backgroundColor: "transparent",
    tooltip: {
      formatter: (params: { dataType?: string; name?: string; data?: Record<string, unknown> }) => {
        if (params.dataType === "node") {
          if (isMacro) {
            const count = (params.data?.value as number) ?? 0;
            return `<b>${params.name}</b><br/>${count} DataObjects`;
          }
          const d = dos.find((x) => x.id === params.data?.value) as LineageDO | undefined;
          if (!d) return params.name ?? "";
          const desc = d.description
            ? `<br/><span style="color:#999;font-size:11px">${String(d.description).substring(0, 120)}${d.description.length > 120 ? "…" : ""}</span>`
            : "";
          return `<b>${d.name ?? String(d.id)}</b><br/><span style="color:#888;font-size:11px">Status: ${d.status ?? "—"}</span>${desc}`;
        }
        if (params.dataType === "edge") {
          const data = params.data as { _kind?: string } | undefined;
          return data?._kind === "predecessor"
            ? `<span style="color:#FCA54D">⟶</span> predecessor relationship`
            : `<span style="color:#888">⟶</span> parent / child hierarchy`;
        }
        return "";
      },
    },
    series: [
      {
        ...baseGraphSeriesConfig(),
        layout: "none",
        draggable: false,
        nodes,
        edges,
        emphasis: {
          focus: "adjacency",
          lineStyle: { width: 2 },
        },
        label: {
          position: "bottom",
          fontSize: 11,
          color: "inherit",
          formatter: (params: { name?: string }) => truncateLabel(params.name ?? "", 18),
        },
      },
    ],
  };
});

// ---------------------------------------------------------------------------
// Minimap option — same node set as detail mode, no labels, tiny markers,
// roam disabled. Cheap because Canvas-backed ECharts re-uses the layout.
// ---------------------------------------------------------------------------

const minimapVisible = ref<boolean>(true);

const minimapOption = computed(() => {
  const dos = visibleDos.value;
  if (!dos.length) return {};
  const pos = positions.value;
  const nodes = dos.map((d) => {
    const p = pos.get(d.id) ?? { x: 0, y: 0 };
    return {
      id: String(d.id),
      x: p.x,
      y: p.y,
      itemStyle: { color: nodeColor(d.status ?? "") },
      symbolSize: 2,
      label: { show: false },
    };
  });
  const edges = visibleEdges.value.map((e) => ({
    source: e.source,
    target: e.target,
    lineStyle: { color: "#cccccc", opacity: 0.4, width: 0.5 },
  }));
  return {
    backgroundColor: "transparent",
    series: [
      {
        type: "graph",
        layout: "none",
        roam: false,
        edgeSymbol: ["none", "none"],
        silent: true,
        nodes,
        edges,
      },
    ],
  };
});

// ---------------------------------------------------------------------------
// Click-through — navigate to DO detail page on node click in detail/meso mode.
// In macro mode, drill into the cluster (set process-type / ply filter).
// ---------------------------------------------------------------------------

// ECharts surfaces a fairly loose union for click events; we read only the
// two fields we own (`dataType` + `data.id` / `data.value`). The unknown
// cast is the cheapest way to bridge the runtime contract without pulling
// in `ECElementEvent` (which forces `data: OptionDataItem | null`).
function onChartClick(rawParams: unknown): void {
  const params = rawParams as { dataType?: string; data?: { id?: string; value?: number } };
  if (params.dataType !== "node") return;
  if (lod.value === "macro") {
    const id = params.data?.id;
    if (!id) return;
    if (id.startsWith("cluster:proc:")) {
      processTypeFilter.value = [id.slice("cluster:proc:".length)];
    } else if (id.startsWith("cluster:ply:")) {
      // Ply-cluster has no first-class filter pill; just zoom in.
      zoom.value = Math.max(zoom.value, 1);
    }
    return;
  }
  const doId = params.data?.value;
  if (typeof doId !== "number") return;
  // Per CLAUDE.md "appId routes": prefer the v2 appId (UUID v7) over the
  // numeric Neo4j id. The dataObjects payload (DataObjectListItemV2) carries
  // both — look up by numeric id and emit the appId when present. Falls back
  // to the numeric id on the rare row that lacks an appId (legacy v1 path).
  const colSegment = props.collectionAppId ?? props.collectionId;
  const matched = dataObjects.value.find(
    d => (d as unknown as { id?: number }).id === doId,
  );
  const doAppId = (matched as unknown as { appId?: string | null } | undefined)?.appId;
  const doSegment = doAppId ?? doId;
  void router.push(`/collections/${colSegment}/dataobjects/${doSegment}`);
}

// ---------------------------------------------------------------------------
// Total / capped count for the info banner
// ---------------------------------------------------------------------------

const totalCount = computed(() => allDos.value.length);
const renderedCount = computed(() => visibleDos.value.length);
</script>

<template>
  <div>
    <!-- (a) Loading state -->
    <div v-if="lineageState === 'loading'" role="status" class="d-flex justify-center pa-8">
      <v-progress-circular indeterminate aria-label="Loading lineage graph" />
    </div>
    <!-- (b) No DataObjects at all -->
    <div
      v-else-if="lineageState === 'no-dos'"
      class="pa-4 text-body-2 text-medium-emphasis"
    >
      No datasets in this collection yet.
    </div>
    <!-- (c) DataObjects exist but no lineage edges defined -->
    <div
      v-else-if="lineageState === 'no-edges' && !anyFilterActive"
      class="pa-4 text-body-2 text-medium-emphasis"
    >
      Datasets exist but no lineage links are defined. Use Predecessor/Successor on a DataObject to connect them.
    </div>
    <div v-else>
      <!-- Filter pill row -->
      <div class="d-flex flex-wrap align-center gap-2 mb-2 px-2" data-testid="lineage-filter-row">
        <span class="text-caption text-medium-emphasis mr-1">Filter:</span>
        <v-menu :close-on-content-click="false" location="bottom start">
          <template #activator="{ props: actv }">
            <v-chip
              v-bind="actv"
              size="small"
              variant="tonal"
              :color="statusFilter.length > 0 ? 'primary' : undefined"
              prepend-icon="mdi-filter-variant"
              data-testid="lineage-filter-status-button"
            >
              Status
              <span v-if="statusFilter.length > 0" class="ml-1">({{ statusFilter.length }})</span>
            </v-chip>
          </template>
          <v-list density="compact">
            <v-list-item
              v-for="s in availableStatuses"
              :key="s"
              :active="statusFilter.includes(s)"
              @click="statusFilter.includes(s)
                ? statusFilter = statusFilter.filter(x => x !== s)
                : statusFilter = [...statusFilter, s]"
            >
              <template #prepend>
                <v-icon size="14" :color="STATUS_COLORS[s]">mdi-circle</v-icon>
              </template>
              <v-list-item-title>{{ s }}</v-list-item-title>
            </v-list-item>
            <v-list-item v-if="availableStatuses.length === 0">
              <v-list-item-subtitle>No statuses on these datasets</v-list-item-subtitle>
            </v-list-item>
          </v-list>
        </v-menu>

        <v-menu :close-on-content-click="false" location="bottom start">
          <template #activator="{ props: actv }">
            <v-chip
              v-bind="actv"
              size="small"
              variant="tonal"
              :color="processTypeFilter.length > 0 ? 'primary' : undefined"
              prepend-icon="mdi-cog-outline"
              data-testid="lineage-filter-process-button"
            >
              Process type
              <span v-if="processTypeFilter.length > 0" class="ml-1">({{ processTypeFilter.length }})</span>
            </v-chip>
          </template>
          <v-list density="compact">
            <v-list-item
              v-for="p in availableProcessTypes"
              :key="p"
              :active="processTypeFilter.includes(p)"
              @click="processTypeFilter.includes(p)
                ? processTypeFilter = processTypeFilter.filter(x => x !== p)
                : processTypeFilter = [...processTypeFilter, p]"
            >
              <v-list-item-title>{{ p }}</v-list-item-title>
            </v-list-item>
            <v-list-item v-if="availableProcessTypes.length === 0">
              <v-list-item-subtitle>No process-type annotations on these datasets</v-list-item-subtitle>
            </v-list-item>
          </v-list>
        </v-menu>

        <v-chip
          v-if="neighborhoodCenter !== null"
          size="small"
          variant="tonal"
          color="primary"
          closable
          prepend-icon="mdi-target"
          data-testid="lineage-filter-neighborhood-chip"
          @click:close="neighborhoodCenter = null"
        >
          Around #{{ neighborhoodCenter }} · depth ≤ {{ neighborhoodDepth }}
        </v-chip>

        <v-btn
          v-if="anyFilterActive"
          size="x-small"
          variant="text"
          density="compact"
          data-testid="lineage-filter-reset"
          @click="resetFilters"
        >
          Reset
        </v-btn>

        <v-spacer />

        <v-btn
          size="x-small"
          variant="text"
          density="compact"
          :prepend-icon="minimapVisible ? 'mdi-eye-off-outline' : 'mdi-eye-outline'"
          data-testid="lineage-minimap-toggle"
          @click="minimapVisible = !minimapVisible"
        >
          {{ minimapVisible ? "Hide minimap" : "Show minimap" }}
        </v-btn>
      </div>

      <!-- Density banner — only when filtering or showing > 500 nodes -->
      <v-alert
        v-if="anyFilterActive || totalCount > 500"
        type="info"
        density="compact"
        variant="tonal"
        class="mb-2"
        data-testid="lineage-count-banner"
      >
        <template v-if="anyFilterActive">
          Showing {{ renderedCount }} of {{ totalCount }} datasets after filtering.
        </template>
        <template v-else>
          {{ totalCount }} datasets — zoom out for a macro overview, zoom in for labels.
        </template>
      </v-alert>

      <!-- Caption -->
      <div class="text-caption text-medium-emphasis mb-2 px-2">
        <v-icon size="14" class="mr-1">mdi-circle</v-icon>Colored by status —
        scroll to zoom · drag canvas to pan · hover node for details · click to open ·
        zoom mode:
        <strong>{{ lod }}</strong>
      </div>

      <!-- Graph + minimap, side-by-side at md+, stacked on small screens -->
      <div class="lineage-canvas-wrapper">
        <v-sheet rounded="lg" class="lineage-main-sheet">
          <ClientOnly>
            <v-chart
              :option="chartOption"
              class="lineage-main-chart"
              autoresize
              data-testid="lineage-main-chart"
              @click="onChartClick"
              @georoam="onGraphRoam"
            />
          </ClientOnly>
        </v-sheet>
        <v-sheet
          v-if="minimapVisible"
          rounded="lg"
          class="lineage-minimap-sheet"
          data-testid="lineage-minimap"
        >
          <ClientOnly>
            <v-chart :option="minimapOption" class="lineage-minimap-chart" autoresize />
          </ClientOnly>
          <div class="lineage-minimap-label">Overview</div>
        </v-sheet>
      </div>

      <!-- Status legend -->
      <div class="d-flex flex-wrap gap-2 mt-2 px-2">
        <v-chip
          v-for="(color, status) in STATUS_COLORS"
          :key="status"
          size="x-small"
          variant="tonal"
          :color="color"
        >{{ status }}</v-chip>
        <v-chip size="x-small" variant="outlined" color="secondary" class="ml-4">
          — parent/child
        </v-chip>
        <v-chip size="x-small" variant="outlined" color="warning">
          ‐ ‐ predecessor
        </v-chip>
      </div>
    </div>
  </div>
</template>

<style scoped>
.lineage-canvas-wrapper {
  display: flex;
  flex-direction: row;
  gap: 8px;
  overflow: hidden;
}
.lineage-main-sheet {
  flex: 1 1 auto;
  overflow: hidden;
}
.lineage-main-chart {
  height: 540px;
  width: 100%;
}
.lineage-minimap-sheet {
  flex: 0 0 200px;
  position: relative;
  overflow: hidden;
  border: 1px solid rgba(127, 127, 127, 0.18);
}
.lineage-minimap-chart {
  height: 540px;
  width: 200px;
}
.lineage-minimap-label {
  position: absolute;
  bottom: 4px;
  right: 8px;
  font-size: 10px;
  color: rgba(127, 127, 127, 0.6);
  pointer-events: none;
}
@media (max-width: 960px) {
  .lineage-canvas-wrapper {
    flex-direction: column;
  }
  .lineage-minimap-sheet {
    flex: 0 0 auto;
  }
  .lineage-minimap-chart {
    height: 140px;
    width: 100%;
  }
}
</style>
