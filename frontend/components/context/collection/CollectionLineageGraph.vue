<script setup lang="ts">
import VChart from "vue-echarts";
import { use } from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import { GraphChart } from "echarts/charts";
import { TooltipComponent, LegendComponent } from "echarts/components";
import dagre from "@dagrejs/dagre";
import type { DataObjectListItemV2 } from "@dlr-shepard/backend-client";
import { useFetchAllDataObjects } from "~/composables/context/useFetchAllDataObjects";

if (process.client) {
  use([CanvasRenderer, GraphChart, TooltipComponent, LegendComponent]);
}

const props = defineProps<{ collectionId: number; collectionAppId?: string }>();

const collectionAppIdRef = computed(() => props.collectionAppId ?? null);
const { dataObjects, loading } = useFetchAllDataObjects(props.collectionId, collectionAppIdRef);

const NODE_CAP = 150;

const STATUS_COLORS: Record<string, string> = {
  DRAFT:     "#8C8C8C",
  IN_REVIEW: "#FCA54D",
  READY:     "#4097CC",
  PUBLISHED: "#7ECA8F",
  ARCHIVED:  "#B799DB",
};

function statusColor(do_: DataObjectListItemV2): string {
  return STATUS_COLORS[(do_ as any).status ?? ""] ?? "#8C8C8C";
}

function doLabel(do_: DataObjectListItemV2): string {
  return (do_ as any).name ?? String((do_ as any).id);
}

function dagreLayout(dos: DataObjectListItemV2[]): Map<number, { x: number; y: number }> {
  const g = new dagre.graphlib.Graph();
  g.setGraph({ rankdir: "LR", nodesep: 55, ranksep: 200, marginx: 60, marginy: 40 });
  g.setDefaultEdgeLabel(() => ({}));

  const visibleIds = new Set(dos.map(d => (d as any).id as number));

  for (const d of dos) {
    g.setNode(String((d as any).id), { width: 90, height: 30 });
  }
  for (const d of dos) {
    for (const predId of ((d as any).predecessorIds ?? []) as number[]) {
      if (visibleIds.has(predId)) {
        g.setEdge(String(predId), String((d as any).id));
      }
    }
    const parentId = (d as any).parentId as number | null;
    if (parentId && visibleIds.has(parentId)) {
      g.setEdge(String(parentId), String((d as any).id));
    }
  }

  dagre.layout(g);

  const positions = new Map<number, { x: number; y: number }>();
  for (const d of dos) {
    const node = g.node(String((d as any).id));
    positions.set((d as any).id as number, { x: node.x, y: node.y });
  }
  return positions;
}

const capped     = computed(() => dataObjects.value.length > NODE_CAP);
const visibleDos = computed(() => dataObjects.value.slice(0, NODE_CAP));

const chartOption = computed(() => {
  const dos = visibleDos.value;
  if (!dos.length) return {};

  const visibleIds = new Set(dos.map(d => (d as any).id as number));
  const positions  = dagreLayout(dos);

  const nodes = dos.map(d => {
    const id  = (d as any).id as number;
    const pos = positions.get(id) ?? { x: 0, y: 0 };
    return {
      id:         String(id),
      name:       doLabel(d),
      value:      id,
      x:          pos.x,
      y:          pos.y,
      itemStyle:  { color: statusColor(d) },
      symbolSize: 22,
      label:      { show: true, fontSize: 11 },
    };
  });

  const edges: Array<{ source: string; target: string; lineStyle: object }> = [];
  dos.forEach(d => {
    const id       = (d as any).id as number;
    const parentId = (d as any).parentId as number | null;
    if (parentId && visibleIds.has(parentId)) {
      edges.push({
        source:    String(parentId),
        target:    String(id),
        lineStyle: { type: "solid", color: "#888", opacity: 0.5 },
      });
    }
    for (const predId of ((d as any).predecessorIds ?? []) as number[]) {
      if (visibleIds.has(predId)) {
        edges.push({
          source:    String(predId),
          target:    String(id),
          lineStyle: { type: "dashed", color: "#FCA54D", opacity: 0.8 },
        });
      }
    }
  });

  return {
    backgroundColor: "transparent",
    tooltip: {
      formatter: (params: any) => {
        if (params.dataType === "node") {
          const d = dos.find(x => (x as any).id === params.data.value) as any;
          if (!d) return params.name;
          const desc = d.description
            ? `<br/><span style="color:#999;font-size:11px">${String(d.description).substring(0, 120)}${d.description.length > 120 ? "…" : ""}</span>`
            : "";
          return `<b>${doLabel(d)}</b><br/><span style="color:#888;font-size:11px">Status: ${d.status ?? "—"}</span>${desc}`;
        }
        if (params.dataType === "edge") {
          return params.data.lineStyle?.type === "dashed"
            ? `<span style="color:#FCA54D">⟶</span> predecessor relationship`
            : `<span style="color:#888">⟶</span> parent / child hierarchy`;
        }
        return "";
      },
    },
    series: [
      {
        type:      "graph",
        layout:    "none",
        roam:      true,
        draggable: false,
        edgeSymbol:     ["none", "arrow"],
        edgeSymbolSize: [0, 8],
        nodes,
        edges,
        emphasis: {
          focus:     "adjacency",
          lineStyle: { width: 2 },
        },
        lineStyle: { curveness: 0.15 },
        label: {
          position: "bottom",
          fontSize:  11,
          color:    "inherit",
          formatter: (params: any) => {
            const n: string = params.name ?? "";
            return n.length > 18 ? n.substring(0, 16) + "…" : n;
          },
        },
      },
    ],
  };
});
</script>

<template>
  <div>
    <div v-if="loading" class="d-flex justify-center pa-8">
      <v-progress-circular indeterminate />
    </div>
    <div
      v-else-if="!dataObjects.length"
      class="pa-4 text-body-2 text-medium-emphasis"
    >
      No datasets in this collection.
    </div>
    <div v-else>
      <v-alert
        v-if="capped"
        type="info"
        density="compact"
        variant="tonal"
        class="mb-2"
        :text="`Showing ${NODE_CAP} of ${dataObjects.length} datasets. Use filters on the Datasets tab to narrow down.`"
      />
      <div class="text-caption text-medium-emphasis mb-2 px-2">
        <v-icon size="14" class="mr-1">mdi-circle</v-icon>Colored by status —
        scroll to zoom · drag canvas to pan · hover node for details
      </div>
      <v-sheet rounded="lg" style="overflow: hidden">
        <ClientOnly>
          <v-chart :option="chartOption" style="height: 440px" autoresize />
        </ClientOnly>
      </v-sheet>
      <div class="d-flex flex-wrap gap-2 mt-2 px-2">
        <v-chip
          v-for="(color, status) in { DRAFT: '#8C8C8C', IN_REVIEW: '#FCA54D', READY: '#4097CC', PUBLISHED: '#7ECA8F', ARCHIVED: '#B799DB' }"
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
