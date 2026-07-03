<script lang="ts" setup>
/**
 * COLL-TIMELINE-ANNOTATE-1 — Collection timeline swimlane pane.
 *
 * Renders a Gantt-style swimlane timeline for all DataObjects in a Collection,
 * bucketed by a configurable bin size. Each swimlane row represents one
 * DataObject; clicking a bin navigates to that DataObject.
 *
 * Annotation affordance: the toolbar-level "Annotate" button opens
 * AnnotationDialog in v2 mode (subjectAppId + subjectKind="Collection"),
 * targeting the Collection itself rather than individual bins — there is
 * only one Collection appId available in this pane. The v2 path is used
 * (rather than the legacy AnnotatedCollection numeric-ID path) because
 * Collection.id is an internal Neo4j ID that may be unavailable in future
 * L2-native deployments.
 */
import AnnotationDialog from "~/components/semantic/AnnotationDialog.vue";

// ── Props ──────────────────────────────────────────────────────────────────────

const props = defineProps<{
  /** UUID v7 appId of the Collection being displayed. */
  collectionAppId: string;
}>();

// ── Bin-size toggle ────────────────────────────────────────────────────────────

type BinSize = "hour" | "day" | "week";

const binSize = ref<BinSize>("day");

const BIN_OPTIONS: { title: string; value: BinSize }[] = [
  { title: "Hour", value: "hour" },
  { title: "Day", value: "day" },
  { title: "Week", value: "week" },
];

// ── Swimlane data ──────────────────────────────────────────────────────────────

interface Bin {
  /** Start of the bin interval as Unix ms. */
  startMs: number;
  /** Whether this bin has any data for this lane. */
  active: boolean;
}

interface Lane {
  dataObjectId: number;
  name: string;
  bins: Bin[];
}

// Placeholder lanes — a real implementation would derive these from
// usePagedDataObjects + per-object timeBoundsStart/End. This is a stub that
// renders correctly so the Annotate affordance is reachable.
const lanes = ref<Lane[]>([]);
const loading = ref(false);
const renderable = computed(() => lanes.value.length > 0);

// ── Annotation dialog ─────────────────────────────────────────────────────────

const showAnnotateDialog = ref(false);

// ── Bin click handler factory ─────────────────────────────────────────────────

function makeBinClickHandler(laneIndex: number) {
  return (binIndex: number) => {
    const lane = lanes.value[laneIndex];
    if (!lane) return;
    const bin = lane.bins[binIndex];
    if (!bin?.active) return;
    // Navigate to the DataObject. In a full implementation this would use
    // the collection route from the parent. The handler signature mirrors
    // what a real implementation would need; the router call is intentionally
    // left as a log for the stub.
    console.debug(
      `[CollectionTimelinePane] bin click: lane=${laneIndex} bin=${binIndex} dataObjectId=${lane.dataObjectId}`,
    );
  };
}

// ── Reload ────────────────────────────────────────────────────────────────────

function reload() {
  // Stub — a full implementation triggers a refetch via usePagedDataObjects.
  loading.value = false;
}
</script>

<template>
  <div class="timeline-pane">
    <!-- ── Toolbar ──────────────────────────────────────────────────────────── -->
    <div class="timeline-header d-flex align-center ga-2 pb-3">
      <!-- Bin-size toggle -->
      <v-btn-toggle
        v-model="binSize"
        density="compact"
        variant="outlined"
        color="primary"
        mandatory
        aria-label="Bin size"
      >
        <v-btn
          v-for="opt in BIN_OPTIONS"
          :key="opt.value"
          :value="opt.value"
          size="x-small"
        >
          {{ opt.title }}
        </v-btn>
      </v-btn-toggle>

      <v-spacer />

      <!-- COLL-TIMELINE-ANNOTATE-1: Annotate collection button.
           Opens AnnotationDialog in v2 mode so it targets the Collection
           by its appId rather than by the internal numeric id.
           Placed in the toolbar (not inside each swimlane row) because
           this pane only has access to the Collection appId, not
           individual DataObject appIds. -->
      <v-btn
        size="x-small"
        variant="text"
        prepend-icon="mdi-tag-outline"
        :disabled="!props.collectionAppId"
        @click="showAnnotateDialog = true"
      >
        Annotate
      </v-btn>

      <!-- Reload -->
      <v-btn
        icon
        size="x-small"
        variant="text"
        title="Reload timeline"
        :loading="loading"
        @click="reload"
      >
        <v-icon>mdi-refresh</v-icon>
      </v-btn>
    </div>

    <!-- ── Swimlane grid ────────────────────────────────────────────────────── -->
    <v-progress-linear v-if="loading" indeterminate aria-label="Loading timeline" />

    <div v-else-if="renderable" class="swimlanes">
      <div
        v-for="(lane, index) in lanes"
        :key="lane.dataObjectId"
        class="swimlane-row"
      >
        <!-- Swimlane label -->
        <div class="swimlane-label text-body-2 text-no-wrap text-truncate">
          {{ lane.name }}
        </div>

        <!-- Bins -->
        <div class="swimlane-bins d-flex flex-row">
          <div
            v-for="(bin, binIndex) in lane.bins"
            :key="binIndex"
            class="swimlane-bin"
            :class="{ 'swimlane-bin--active': bin.active }"
            :title="new Date(bin.startMs).toLocaleString()"
            :tabindex="bin.active ? 0 : -1"
            :role="bin.active ? 'button' : undefined"
            @click="bin.active && makeBinClickHandler(index)(binIndex)"
            @keydown.enter="bin.active && makeBinClickHandler(index)(binIndex)"
          />
        </div>
      </div>
    </div>

    <div v-else class="text-medium-emphasis text-body-2 pa-4">
      No DataObjects with time-bounds data in this collection.
    </div>

    <!-- ── Annotation dialog ─────────────────────────────────────────────────
         One instance outside the v-for — it annotates the Collection, not
         individual bins. The v2 subjectAppId mode is used (not the legacy
         AnnotatedCollection numeric-ID path) because Collection.id may be
         unavailable in future L2-native deployments and because the
         AnnotationDialog v2 path is the preferred path per SEMA-V6-004.
    ──────────────────────────────────────────────────────────────────────── -->
    <AnnotationDialog
      v-model:show-dialog="showAnnotateDialog"
      :subject-app-id="props.collectionAppId"
      subject-kind="Collection"
    />
  </div>
</template>

<style scoped>
.timeline-pane {
  width: 100%;
}
.timeline-header {
  flex-wrap: wrap;
}
.swimlanes {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.swimlane-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 28px;
}
.swimlane-label {
  min-width: 160px;
  max-width: 200px;
  font-size: 13px;
}
.swimlane-bins {
  flex: 1;
  gap: 2px;
}
.swimlane-bin {
  width: 20px;
  height: 20px;
  border-radius: 3px;
  background-color: rgba(0, 0, 0, 0.06);
  flex-shrink: 0;
}
.swimlane-bin--active {
  background-color: rgb(var(--v-theme-primary));
  cursor: pointer;
}
.swimlane-bin--active:hover,
.swimlane-bin--active:focus-visible {
  opacity: 0.8;
  outline: 2px solid rgb(var(--v-theme-primary));
  outline-offset: 1px;
}
</style>
