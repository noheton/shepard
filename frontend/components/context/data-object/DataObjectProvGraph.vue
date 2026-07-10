<script setup lang="ts">
import VChart from "vue-echarts";
import { use } from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import { GraphChart } from "echarts/charts";
import { TooltipComponent } from "echarts/components";
import type { DataObject, Activity } from "@dlr-shepard/backend-client";
import { ProvenanceApi } from "@dlr-shepard/backend-client";
import type { CallbackDataParams } from "echarts/types/dist/shared";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";
import { useFetchAllDataObjects } from "~/composables/context/useFetchAllDataObjects";
import { ACTION_COLORS, nodeColor, truncateLabel, baseGraphSeriesConfig } from "~/composables/useLineageGraph";

if (import.meta.client) {
  use([CanvasRenderer, GraphChart, TooltipComponent]);
}

interface EChartsGraphNode {
  id: string;
  name: string;
  category: number;
  symbolSize: number;
  symbol: string;
  itemStyle?: Record<string, unknown>;
  label?: Record<string, unknown>;
  tooltip?: { formatter: string };
  fixed?: boolean;
  x?: number;
  y?: number;
}

interface EChartsGraphEdge {
  source: string;
  target: string;
  lineStyle?: Record<string, unknown>;
  label?: Record<string, unknown>;
}

const props = defineProps<{
  dataObject: DataObject;
  collectionAppId: string;
  collectionId?: number;
}>();

const activities = ref<Activity[]>([]);
const loading = ref(false);

const { dataObjects: collectionDataObjects } = useFetchAllDataObjects(
  props.collectionAppId,
  props.collectionId,
);

const doNameMap = computed(() => {
  const map = new Map<number, string>();
  collectionDataObjects.value.forEach(d => {
    if (d.id !== undefined) map.set(d.id, d.name ?? `Dataset #${d.id}`);
  });
  return map;
});

function resolveName(id: number): string {
  return doNameMap.value.get(id) ?? `Dataset #${id}`;
}

onMounted(async () => {
  if (!props.dataObject.appId) return;
  loading.value = true;
  try {
    const paged = await useV2ShepardApi(ProvenanceApi).value.listActivities({
      targetAppId: props.dataObject.appId,
      pageSize: 50,
    });
    activities.value = Array.isArray(paged) ? paged : ((paged as { items?: unknown[] })?.items ?? []) as Activity[];
  } catch {
    activities.value = [];
  } finally {
    loading.value = false;
  }
});

const chartOption = computed(() => {
  const do_ = props.dataObject;
  const acts = activities.value;

  // central entity node
  const nodes: EChartsGraphNode[] = [
    {
      id: "entity",
      name: do_.name ?? String(do_.id),
      category: 0,
      symbolSize: 40,
      symbol: "roundRect",
      itemStyle: { color: "#4097CC" },
      label: { show: true, position: "bottom" },
      fixed: true,
      x: 0, y: 0,
    },
  ];

  const edges: EChartsGraphEdge[] = [];

  // predecessor nodes
  (do_.predecessorIds ?? []).slice(0, 6).forEach((predId) => {
    const nid = `pred_${predId}`;
    nodes.push({
      id: nid,
      name: resolveName(predId),
      category: 1,
      symbolSize: 24,
      symbol: "circle",
      itemStyle: { color: "#FCA54D" },
      label: { show: true, position: "bottom", fontSize: 10 },
      tooltip: { formatter: `<b>${resolveName(predId)}</b><br/><span style="color:#888;font-size:11px">predecessor</span>` },
    });
    edges.push({
      source: nid, target: "entity",
      lineStyle: { type: "dashed", color: "#FCA54D", width: 1.5 },
      label: { show: false },
    });
  });

  // child nodes
  (do_.childrenIds ?? []).slice(0, 6).forEach((childId) => {
    const nid = `child_${childId}`;
    nodes.push({
      id: nid,
      name: resolveName(childId),
      category: 1,
      symbolSize: 24,
      symbol: "circle",
      itemStyle: { color: "#7ECA8F" },
      label: { show: true, position: "bottom", fontSize: 10 },
      tooltip: { formatter: `<b>${resolveName(childId)}</b><br/><span style="color:#888;font-size:11px">child dataset</span>` },
    });
    edges.push({
      source: "entity", target: nid,
      lineStyle: { type: "solid", color: "#7ECA8F", width: 1.5 },
      label: { show: false },
    });
  });

  // unique agents
  const agentSet = new Set(acts.map(a => a.agentUsername));
  const agentArr = [...agentSet];
  agentArr.forEach((agent) => {
    const nid = `agent_${agent}`;
    const agentActs = acts.filter(a => a.agentUsername === agent);
    const kindCount = agentActs.reduce<Record<string, number>>((m, a) => {
      m[a.actionKind] = (m[a.actionKind] ?? 0) + 1;
      return m;
    }, {});
    const summary = Object.entries(kindCount)
      .map(([k, v]) => `${k}×${v}`)
      .join(", ");
    nodes.push({
      id: nid,
      name: agent,
      category: 2,
      symbolSize: 28,
      symbol: "diamond",
      itemStyle: { color: "#B799DB" },
      tooltip: { formatter: `${agent}: ${summary}` },
      label: { show: true, position: "bottom", fontSize: 10 },
    });
    // most recent action kind for edge color
    const latestAct = agentActs.sort((a, b) => b.startedAtMillis - a.startedAtMillis)[0];
    edges.push({
      source: nid,
      target: "entity",
      lineStyle: {
        type: "solid",
        color: nodeColor(latestAct?.actionKind ?? "", ACTION_COLORS),
        width: 1.5,
      },
      label: { show: true, formatter: summary, fontSize: 9 },
    });
  });

  return {
    backgroundColor: "transparent",
    tooltip: {
      enterable: false,
      formatter: (params: CallbackDataParams) => {
        if (params.dataType === "node") {
          const node = params.data as EChartsGraphNode;
          if (node.tooltip) return node.tooltip.formatter;
          return `<b>${params.name}</b>`;
        }
        if (params.dataType === "edge") {
          const ls = (params.data as EChartsGraphEdge).lineStyle as { type?: string } | undefined;
          return ls?.type === "dashed" ? "predecessor → this dataset" : "this dataset → child / agent action";
        }
        return "";
      },
    },
    series: [
      {
        ...baseGraphSeriesConfig(),
        layout: "force",
        draggable: true,
        edgeSymbolSize: [0, 7],
        force: {
          repulsion: 200,
          gravity: 0.03,
          edgeLength: [80, 160],
        },
        nodes,
        edges,
        emphasis: { focus: "adjacency" },
        label: {
          color: "inherit",
          formatter: (params: CallbackDataParams) => {
            const n: string = params.name ?? "";
            return truncateLabel(n, 16);
          },
        },
      },
    ],
  };
});
</script>

<template>
  <div>
    <div v-if="loading" role="status" class="d-flex justify-center pa-6">
      <v-progress-circular indeterminate size="24" aria-label="Loading provenance graph" />
    </div>
    <div v-else>
      <div class="d-flex flex-wrap align-center ga-1 mb-2 text-caption text-medium-emphasis">
        <v-chip size="x-small" color="primary" variant="tonal">this dataset</v-chip>
        <v-chip size="x-small" color="warning" variant="tonal">predecessors</v-chip>
        <v-chip size="x-small" color="success" variant="tonal">children</v-chip>
        <v-chip size="x-small" color="secondary" variant="tonal">agents</v-chip>
        <span class="ml-1">· scroll to zoom, drag to pan</span>
      </div>
      <v-sheet rounded="lg" style="overflow: hidden">
        <ClientOnly>
          <v-chart :option="chartOption" style="height: 320px" autoresize />
        </ClientOnly>
      </v-sheet>
      <div
        v-if="activities.length === 0"
        class="text-caption text-medium-emphasis mt-2"
      >
        No provenance activities recorded for this dataset yet.
      </div>
    </div>
  </div>
</template>
