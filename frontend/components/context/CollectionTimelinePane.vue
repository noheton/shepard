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
import {
  useCollectionTimeline,
  fetchCollectionsForCompare,
  type CollectionSummary,
} from "~/composables/context/useCollectionTimeline";
import {
  binAnnotateMenuTitle,
  buildLaneOption,
  drillDownPath,
  hasRenderableData,
  BIN_SIZE_CHOICES,
  EMPTY_STATE_HINT,
} from "~/utils/collectionTimeline";
import { AnnotatedCollection } from "~/composables/annotated";
import AddAnnotationDialog from "~/components/context/semantic/annotation/add-dialog/AddAnnotationDialog.vue";

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
const { envelope, loading, error, fetchTimeline, fetchCrossTimeline } = useCollectionTimeline();

/** Bin action menu — shown when user clicks a chart bar */
const pendingBin = ref<{ laneKey: string; laneLabel: string; day: string } | null>(null);
const showBinActionMenu = ref(false);
const showAnnotateDialog = ref(false);

const annotatedCollection = computed(() => new AnnotatedCollection(props.collectionAppId));

const renderable = computed(() => hasRenderableData(envelope.value));
const echoedBinSize = computed(() => envelope.value?.binSizeDays ?? binSizeDays.value);

const lanes = computed(() => envelope.value?.lanes ?? []);

// ── "Compare with" state ─────────────────────────────────────────────────────

const compareIds = ref<string[]>([]);
const selectorItems = ref<CollectionSummary[]>([]);
const selectorLoading = ref(false);

async function loadSelectorItems(): Promise<void> {
  selectorLoading.value = true;
  try {
    const { data: session } = useAuth();
    const token = session.value?.accessToken;
    if (!token) return;
    selectorItems.value = await fetchCollectionsForCompare(token);
  } catch {
    // autocomplete best-effort — silent failure is acceptable
  } finally {
    selectorLoading.value = false;
  }
}

/** Options for the autocomplete: all accessible collections except the current one. */
const compareOptions = computed(() =>
  selectorItems.value
    .filter((c) => c.appId !== props.collectionAppId)
    .map((c) => ({
      value: c.appId,
      title: c.name ? `${c.name} (${c.appId.slice(0, 8)}…)` : c.appId,
    })),
);

// ── fetch orchestration ──────────────────────────────────────────────────────

async function refresh(): Promise<void> {
  if (!props.collectionAppId) return;
  if (compareIds.value.length > 0) {
    await fetchCrossTimeline([props.collectionAppId, ...compareIds.value], binSizeDays.value);
  } else {
    await fetchTimeline(props.collectionAppId, binSizeDays.value);
  }
}

function onBinSizeChange(value: number | null): void {
  if (typeof value !== "number") return;
  binSizeDays.value = value;
  void refresh();
}

function laneOption(laneIndex: number): Record<string, unknown> {
  const lane = lanes.value[laneIndex];
  if (!lane) return {};
  return buildLaneOption(lane, echoedBinSize.value);
}

/**
 * Bin-click handler — shows an action menu with "View DataObjects" (drill-
 * down) and "Annotate this period" (COLL-TIMELINE-ANNOTATE-1). The previous
 * immediate-navigate behaviour is preserved via onDrillDown().
 */
function onBinClick(laneIndex: number, params: unknown): void {
  const lane = lanes.value[laneIndex];
  if (!lane) return;
  const dataIndex = (params as { dataIndex?: number } | null)?.dataIndex ?? -1;
  if (dataIndex < 0 || dataIndex >= lane.bins.length) return;
  const bin = lane.bins[dataIndex];
  if (!bin) return;
  pendingBin.value = { laneKey: lane.key, laneLabel: lane.label, day: bin.day };
  showBinActionMenu.value = true;
}

function onDrillDown(): void {
  if (!pendingBin.value) return;
  showBinActionMenu.value = false;
  void navigateTo(drillDownPath(props.collectionAppId, pendingBin.value.laneKey, pendingBin.value.day));
}

function onAnnotateBin(): void {
  showBinActionMenu.value = false;
  showAnnotateDialog.value = true;
}

function makeBinClickHandler(laneIndex: number) {
  return (params: unknown): void => onBinClick(laneIndex, params);
}

onMounted(() => {
  void refresh();
  void loadSelectorItems();
});

watch(
  () => props.collectionAppId,
  () => {
    compareIds.value = [];
    void refresh();
  },
);

watch(compareIds, () => {
  void refresh();
});
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
      <!-- COLL-TIMELINE-CROSS-1 — compare-with selector -->
      <v-autocomplete
        v-model="compareIds"
        :items="compareOptions"
        :loading="selectorLoading"
        label="Compare with…"
        density="compact"
        variant="outlined"
        multiple
        chips
        closable-chips
        hide-details
        clearable
        class="compare-selector"
        data-testid="timeline-compare-selector"
      />
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

  <!-- Bin action menu — shown on chart-bar click -->
  <v-dialog v-model="showBinActionMenu" max-width="380" data-testid="bin-action-menu">
    <v-card>
      <v-card-title class="text-subtitle-1 pt-4 pb-0">
        {{ pendingBin
          ? binAnnotateMenuTitle(pendingBin.laneLabel, pendingBin.day, echoedBinSize)
          : '' }}
      </v-card-title>
      <v-card-text class="pa-0">
        <v-list>
          <v-list-item
            prepend-icon="mdi-filter-outline"
            title="View DataObjects"
            subtitle="Filter the DataObjects list to this period"
            @click="onDrillDown"
          />
          <v-list-item
            prepend-icon="mdi-note-plus-outline"
            title="Annotate this period"
            subtitle="Add a semantic annotation to this Collection for this timeline period"
            @click="onAnnotateBin"
          />
        </v-list>
      </v-card-text>
    </v-card>
  </v-dialog>

  <!-- Annotation dialog — collection-level annotation pre-seeded from selected bin -->
  <AddAnnotationDialog
    v-if="showAnnotateDialog"
    v-model:show-dialog="showAnnotateDialog"
    :annotated="annotatedCollection"
    prefill="urn:shepard:quality"
  />
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
.compare-selector {
  min-width: 200px;
  max-width: 340px;
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
