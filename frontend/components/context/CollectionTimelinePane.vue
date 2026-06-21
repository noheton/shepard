<script setup lang="ts">
/**
 * COLL-TIMELINE-1 — Collection-landing Timeline pane.
 *
 * Renders the campaign as a vertical stack of horizontal swimlane bars
 * (one per process-type). Each lane is its own ECharts instance so the
 * shared x-axis sees the union of bin days; the colour band per bar
 * encodes OK / NCR / REJECTED status counts.
 *
 * Lazy-loaded — the parent ExpansionPanelItem only mounts the pane on
 * first expansion, so the Collection-detail initial paint isn't taxed.
 *
 * Reuses the ECharts integration the codebase already pulled in for
 * `CollectionCrossTrackViewPane.vue` (vue-echarts + bar/grid/tooltip
 * components); zero new chart dependencies.
 */
import { useCollectionTimeline } from "~/composables/context/useCollectionTimeline";
import { useCollectionEvents } from "~/composables/context/useCollectionEvents";
import {
  buildLaneOption,
  drillDownPath,
  hasRenderableData,
  BIN_SIZE_CHOICES,
  EMPTY_STATE_HINT,
  LIVE_REFRESH_EVENTS,
} from "~/utils/collectionTimeline";

import VChart from "vue-echarts";
import { use } from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import { BarChart } from "echarts/charts";
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
  DataZoomComponent,
} from "echarts/components";

if (import.meta.client) {
  use([
    CanvasRenderer,
    BarChart,
    GridComponent,
    TooltipComponent,
    LegendComponent,
    DataZoomComponent,
  ]);
}

const props = defineProps<{
  collectionAppId: string;
}>();

const binSizeDays = ref<number>(1);
const { envelope, loading, error, fetchTimeline } = useCollectionTimeline();

const renderable = computed(() => hasRenderableData(envelope.value));
const echoedBinSize = computed(() => envelope.value?.binSizeDays ?? binSizeDays.value);

const lanes = computed(() => envelope.value?.lanes ?? []);

async function refresh(bypassCache = false): Promise<void> {
  if (!props.collectionAppId) return;
  await fetchTimeline(props.collectionAppId, binSizeDays.value, bypassCache);
}

function onBinSizeChange(value: number | null): void {
  if (typeof value !== "number") return;
  binSizeDays.value = value;
  void refresh();
}

// COLL-TIMELINE-LIVE-1 — subscribe to SSE events; debounce rapid bursts of
// creates/updates into a single cache-bypassing refresh so the bins stay current
// without a manual reload.
const liveSubscribed = ref(false);
let liveDebounceTimer: ReturnType<typeof setTimeout> | null = null;

function scheduleLiveRefresh(): void {
  if (liveDebounceTimer !== null) clearTimeout(liveDebounceTimer);
  liveDebounceTimer = setTimeout(() => {
    liveDebounceTimer = null;
    void refresh(true);
  }, 1_500);
}

const { onEvent } = useCollectionEvents(computed(() => props.collectionAppId));
onEvent((event) => {
  liveSubscribed.value = true;
  if (LIVE_REFRESH_EVENTS.has(event.eventType)) {
    scheduleLiveRefresh();
  }
});

onUnmounted(() => {
  if (liveDebounceTimer !== null) {
    clearTimeout(liveDebounceTimer);
    liveDebounceTimer = null;
  }
});

function laneOption(laneIndex: number): Record<string, unknown> {
  const lane = lanes.value[laneIndex];
  if (!lane) return {};
  return buildLaneOption(lane, echoedBinSize.value);
}

/**
 * Bin-click handler — navigate to the data-objects list with the
 * process-type + date filter query params. The list page doesn't yet
 * honour those params (the COLL-TIMELINE-DRILLDOWN-FILTER-1 follow-up
 * handles that); the navigation still lands on a useful page rather
 * than failing silently.
 */
function onBinClick(laneIndex: number, params: unknown): void {
  const lane = lanes.value[laneIndex];
  if (!lane) return;
  const dataIndex = (params as { dataIndex?: number } | null)?.dataIndex ?? -1;
  if (dataIndex < 0 || dataIndex >= lane.bins.length) return;
  const bin = lane.bins[dataIndex];
  if (!bin) return;
  void navigateTo(drillDownPath(props.collectionAppId, lane.key, bin.day));
}

function makeBinClickHandler(laneIndex: number) {
  return (params: unknown): void => onBinClick(laneIndex, params);
}

onMounted(() => {
  void refresh();
});

watch(
  () => props.collectionAppId,
  () => {
    void refresh();
  },
);
</script>

<template>
  <div class="timeline-pane" data-testid="collection-timeline-pane">
    <!-- Toolbar — bin-size toggle + total count + reload -->
    <div class="timeline-header">
      <div class="text-caption text-medium-emphasis">
        <template v-if="envelope">
          {{ envelope.totalDataObjects }} DataObjects · {{ envelope.lanes.length }} lanes
          <span v-if="envelope.binSizeDays !== binSizeDays" class="ml-2">
            (server coarsened to {{ envelope.binSizeDays }}-day bins)
          </span>
        </template>
        <template v-else>Loading timeline…</template>
      </div>
      <v-spacer />
      <v-btn-toggle
        :model-value="binSizeDays"
        density="compact"
        mandatory
        rounded="lg"
        @update:model-value="onBinSizeChange"
      >
        <v-btn
          v-for="choice in BIN_SIZE_CHOICES"
          :key="choice.value"
          :value="choice.value"
          size="small"
        >
          {{ choice.label }}
        </v-btn>
      </v-btn-toggle>
      <v-chip
        v-if="liveSubscribed"
        color="success"
        size="x-small"
        variant="tonal"
        data-testid="timeline-live-chip"
      >
        ● Live
      </v-chip>
      <v-btn
        size="x-small"
        variant="text"
        :loading="loading"
        :disabled="loading"
        @click="refresh"
      >
        Reload
      </v-btn>
    </div>

    <v-alert
      v-if="error"
      type="error"
      density="compact"
      variant="tonal"
      class="mt-2 mb-2"
    >
      {{ error }}
    </v-alert>

    <!-- Empty state — degrades gracefully for non-MFFD Collections. -->
    <div
      v-if="!loading && envelope && !renderable"
      class="empty-state"
      data-testid="timeline-empty-state"
    >
      {{ EMPTY_STATE_HINT }}
    </div>

    <!-- Swimlane stack -->
    <div v-if="renderable" class="swimlanes" data-testid="timeline-swimlanes">
      <div
        v-for="(lane, index) in lanes"
        :key="lane.key"
        class="swimlane"
        :data-lane-key="lane.key"
      >
        <div class="swimlane-label" :title="lane.label">
          <span class="swimlane-label-text">{{ lane.label }}</span>
          <span class="swimlane-label-count text-caption text-medium-emphasis">
            {{ lane.bins.reduce((acc, b) => acc + b.count, 0) }} DOs
          </span>
        </div>
        <div class="swimlane-chart">
          <VChart
            :option="laneOption(index)"
            autoresize
            @click="makeBinClickHandler(index)"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.timeline-pane {
  width: 100%;
}
.timeline-header {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.swimlanes {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 8px;
}
.swimlane {
  display: grid;
  grid-template-columns: 160px 1fr;
  gap: 8px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.012);
  padding: 6px 8px;
  min-height: 96px;
}
.swimlane-label {
  display: flex;
  flex-direction: column;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  overflow: hidden;
}
.swimlane-label-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.swimlane-label-count {
  font-weight: 400;
}
.swimlane-chart {
  min-height: 88px;
  width: 100%;
}
.empty-state {
  margin-top: 16px;
  padding: 16px;
  border: 1px dashed rgba(0, 0, 0, 0.12);
  border-radius: 4px;
  text-align: center;
  font-size: 13px;
  opacity: 0.7;
}
</style>
