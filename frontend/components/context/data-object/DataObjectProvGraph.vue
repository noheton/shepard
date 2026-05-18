<script setup lang="ts">
import VChart from "vue-echarts";
import { use } from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import { GraphChart } from "echarts/charts";
import { TooltipComponent } from "echarts/components";
import type { DataObject } from "@dlr-shepard/backend-client";
import {
  ProvenanceApi,
  type ActivityIO,
} from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";
import { useFetchAllDataObjects } from "~/composables/context/useFetchAllDataObjects";

if (process.client) {
  use([CanvasRenderer, GraphChart, TooltipComponent]);
}

const props = defineProps<{
  dataObject: DataObject;
  collectionId: number;
}>();

const activities = ref<ActivityIO[]>([]);
const loading = ref(false);

const { dataObjects: collectionDataObjects } = useFetchAllDataObjects(props.collectionId);

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
    activities.value = await useV2ShepardApi(ProvenanceApi).value.listActivities({
      targetAppId: props.dataObject.appId,
      limit: 50,
    });
  } catch {
    activities.value = [];
  } finally {
    loading.value = false;
  }
});

const ACTION_COLORS: Record<string, string> = {
  CREATE: "#7ECA8F",
  UPDATE: "#4097CC",
  DELETE: "#E56874",
  READ:   "#8C8C8C",
  EXECUTE: "#B799DB",
};

const chartOption = computed(() => {
  const do_ = props.dataObject;
  const acts = activities.value;

  // central entity node
  const nodes: any[] = [
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

  const edges: any[] = [];

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
  agentArr.forEach((agent, i) => {
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
        color: ACTION_COLORS[latestAct?.actionKind ?? ""] ?? "#8C8C8C",
        width: 1.5,
      },
      label: { show: true, formatter: summary, fontSize: 9 },
    });
  });

  return {
    backgroundColor: "transparent",
    tooltip: {
      enterable: false,
      formatter: (params: any) => {
        if (params.dataType === "node") {
          if (params.data.tooltip) return params.data.tooltip.formatter;
          return `<b>${params.name}</b>`;
        }
        if (params.dataType === "edge") {
          const ls = params.data.lineStyle;
          return ls?.type === "dashed" ? "predecessor → this dataset" : "this dataset → child / agent action";
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
          gravity: 0.03,
          edgeLength: [80, 160],
        },
        edgeSymbol: ["none", "arrow"],
        edgeSymbolSize: [0, 7],
        nodes,
        edges,
        emphasis: { focus: "adjacency" },
        label: {
          color: "inherit",
          formatter: (params: any) => {
            const n: string = params.name ?? "";
            return n.length > 16 ? n.substring(0, 14) + "…" : n;
          },
        },
        lineStyle: { curveness: 0.15 },
      },
    ],
  };
});
</script>

<template>
  <div>
    <div v-if="loading" class="d-flex justify-center pa-6">
      <v-progress-circular indeterminate size="24" />
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
