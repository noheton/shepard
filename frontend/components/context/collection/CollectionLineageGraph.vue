<script setup lang="ts">
import VChart from "vue-echarts";
import { use } from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import { GraphChart } from "echarts/charts";
import { TooltipComponent, LegendComponent } from "echarts/components";
import type { DataObject } from "@dlr-shepard/backend-client";
import { useFetchAllDataObjects } from "~/composables/context/useFetchAllDataObjects";

if (process.client) {
  use([CanvasRenderer, GraphChart, TooltipComponent, LegendComponent]);
}

const props = defineProps<{ collectionId: number }>();

const { dataObjects, loading } = useFetchAllDataObjects(props.collectionId);

const STATUS_COLORS: Record<string, string> = {
  DRAFT:       "#8C8C8C",
  IN_REVIEW:   "#FCA54D",
  READY:       "#4097CC",
  PUBLISHED:   "#7ECA8F",
  ARCHIVED:    "#B799DB",
};

function statusColor(do_: DataObject): string {
  return STATUS_COLORS[do_.status ?? ""] ?? "#8C8C8C";
}

function doLabel(do_: DataObject): string {
  return do_.name ?? String(do_.id);
}

const chartOption = computed(() => {
  const dos = dataObjects.value;
  if (!dos.length) return {};

  const idToIdx = new Map(dos.map((d, i) => [d.id, i]));

  const nodes = dos.map(d => ({
    id: String(d.id),
    name: doLabel(d),
    value: d.id,
    itemStyle: { color: statusColor(d) },
    label: { show: true, fontSize: 11 },
    symbolSize: 22,
  }));

  const edges: Array<{ source: string; target: string; lineStyle: object; label: object }> = [];

  dos.forEach(d => {
    // parent → child (hierarchy)
    if (d.parentId && idToIdx.has(d.parentId)) {
      edges.push({
        source: String(d.parentId),
        target: String(d.id),
        lineStyle: { type: "solid", color: "#888" },
        label: { show: false },
      });
    }
    // predecessor → this (workflow dependency)
    (d.predecessorIds ?? []).forEach(predId => {
      if (idToIdx.has(predId)) {
        edges.push({
          source: String(predId),
          target: String(d.id),
          lineStyle: { type: "dashed", color: "#FCA54D" },
          label: { show: false },
        });
      }
    });
  });

  return {
    backgroundColor: "transparent",
    tooltip: {
      formatter: (params: any) => {
        if (params.dataType === "node") {
          const d = dos.find(x => x.id === params.data.value);
          if (!d) return params.name;
          const desc = d.description ? `<br/><span style="color:#999;font-size:11px">${d.description.substring(0, 100)}${d.description.length > 100 ? "…" : ""}</span>` : "";
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
        type: "graph",
        layout: "force",
        roam: true,
        draggable: true,
        force: {
          repulsion: 200,
          gravity: 0.05,
          edgeLength: [80, 180],
          layoutAnimation: true,
        },
        edgeSymbol: ["none", "arrow"],
        edgeSymbolSize: [0, 8],
        nodes,
        edges,
        emphasis: {
          focus: "adjacency",
          lineStyle: { width: 2 },
        },
        lineStyle: { curveness: 0.1 },
        label: {
          position: "bottom",
          fontSize: 11,
          color: "inherit",
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
      <div class="text-caption text-medium-emphasis mb-2 px-2">
        <v-icon size="14" class="mr-1">mdi-circle</v-icon>Colored by status —
        drag to rearrange · scroll to zoom · click node to highlight connections
      </div>
      <v-sheet
        rounded="lg"
        style="overflow: hidden"
      >
        <ClientOnly>
          <v-chart :option="chartOption" style="height: 420px" autoresize />
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
