<script lang="ts" setup>
/**
 * COLL-TIMELINE-ANNOTATE-1 — Per-DataObject swimlane timeline pane.
 *
 * Renders one swimlane row per DataObject that has timeseries data
 * (timeBoundsStart / timeBoundsEnd populated). Each row shows the
 * DataObject's active time range divided into hour / day / week bins.
 * Clicking an active bin navigates to that DataObject's detail page.
 *
 * Annotation affordance: the toolbar-level "Annotate" button opens
 * AnnotationDialog in v2 mode (subjectAppId + subjectKind="Collection"),
 * targeting the Collection itself.
 */
import AnnotationDialog from "~/components/semantic/AnnotationDialog.vue";
import { usePagedDataObjects } from "~/composables/context/usePagedDataObjects";
import {
  BIN_SIZE_MS,
  computeGlobalMinMs,
  computeGlobalMaxMs,
  computeBinStarts,
} from "~/utils/collectionDataObjectTimeline";

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

// ── Data loading ──────────────────────────────────────────────────────────────

const name = ref("");
const page = ref(0);
const collectionAppIdRef = computed(() => props.collectionAppId);

// collectionId: 0 is a safe dummy — usePagedDataObjects uses collectionAppId
// (the v2 appId) when non-null, ignoring the numeric id entirely.
const { items, loading } = usePagedDataObjects({
  collectionId: 0,
  collectionAppId: collectionAppIdRef,
  name,
  page,
  pageSize: 50,
  includeTimeBounds: true,
});

// ── Swimlane computation ───────────────────────────────────────────────────────

interface Lane {
  dataObjectId: number;
  dataObjectAppId: string | null;
  name: string;
  bins: { startMs: number; active: boolean }[];
}

const doWithBounds = computed(() =>
  items.value.filter(d => d.timeBoundsStart != null && d.timeBoundsEnd != null),
);

const globalMinMs = computed(() => computeGlobalMinMs(doWithBounds.value));
const globalMaxMs = computed(() => computeGlobalMaxMs(doWithBounds.value));
const activeBinSizeMs = computed(() => BIN_SIZE_MS[binSize.value]);
const binStarts = computed(() =>
  computeBinStarts(globalMinMs.value, globalMaxMs.value, activeBinSizeMs.value),
);

const lanes = computed<Lane[]>(() => {
  const starts = binStarts.value;
  const bms = activeBinSizeMs.value;
  return doWithBounds.value.map(d => ({
    dataObjectId: d.id,
    dataObjectAppId: d.appId ?? null,
    name: d.name,
    bins: starts.map(start => ({
      startMs: start,
      // A bin is active when the DataObject's time range overlaps it.
      active:
        new Date(d.timeBoundsStart!).getTime() < start + bms &&
        new Date(d.timeBoundsEnd!).getTime() >= start,
    })),
  }));
});

const renderable = computed(() => lanes.value.length > 0);

// ── Annotation dialog ─────────────────────────────────────────────────────────

const showAnnotateDialog = ref(false);

// ── Navigation ────────────────────────────────────────────────────────────────

const router = useRouter();

function navigateToDo(lane: Lane) {
  const doSegment = lane.dataObjectAppId ?? String(lane.dataObjectId);
  void router.push(`/collections/${props.collectionAppId}/dataobjects/${doSegment}`);
}
</script>

<template>
  <div class="do-timeline-pane">
    <!-- ── Toolbar ──────────────────────────────────────────────────────────── -->
    <div class="do-timeline-header d-flex align-center ga-2 pb-3">
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
           by its appId. Toolbar-level (not per-row) because this pane
           annotates the Collection itself, not individual DataObjects. -->
      <v-btn
        size="x-small"
        variant="text"
        prepend-icon="mdi-tag-outline"
        :disabled="!props.collectionAppId"
        data-test="do-timeline-annotate-btn"
        @click="showAnnotateDialog = true"
      >
        Annotate
      </v-btn>
    </div>

    <!-- ── Swimlane grid ────────────────────────────────────────────────────── -->
    <v-progress-linear v-if="loading" indeterminate aria-label="Loading timeline" />

    <div v-else-if="renderable" class="swimlanes" data-test="do-timeline-grid">
      <div
        v-for="lane in lanes"
        :key="lane.dataObjectId"
        class="swimlane-row"
      >
        <!-- Swimlane label — click navigates to the DataObject detail page -->
        <div
          class="swimlane-label text-body-2 text-no-wrap text-truncate"
          role="button"
          tabindex="0"
          :title="lane.name"
          @click="navigateToDo(lane)"
          @keydown.enter="navigateToDo(lane)"
        >
          {{ lane.name }}
        </div>

        <!-- Bins -->
        <div class="swimlane-bins d-flex flex-row">
          <div
            v-for="(bin, binIndex) in lane.bins"
            :key="binIndex"
            class="swimlane-bin"
            :class="{ 'swimlane-bin--active': bin.active }"
            :title="new Date(bin.startMs).toLocaleDateString()"
            :tabindex="bin.active ? 0 : -1"
            :role="bin.active ? 'button' : undefined"
            :aria-label="bin.active ? `${lane.name} — ${new Date(bin.startMs).toLocaleDateString()}` : undefined"
            @click="bin.active && navigateToDo(lane)"
            @keydown.enter="bin.active && navigateToDo(lane)"
          />
        </div>
      </div>
    </div>

    <div
      v-else-if="!loading"
      class="text-medium-emphasis text-body-2 pa-4"
      data-test="do-timeline-empty"
    >
      No DataObjects with timeseries data in this collection.
    </div>

    <!-- ── Annotation dialog ─────────────────────────────────────────────────
         Annotates the Collection (not a specific DataObject) so one instance
         outside the v-for is correct. Uses v2 subjectAppId path (SEMA-V6-004).
    ──────────────────────────────────────────────────────────────────────── -->
    <AnnotationDialog
      v-model:show-dialog="showAnnotateDialog"
      :subject-app-id="props.collectionAppId"
      subject-kind="Collection"
    />
  </div>
</template>

<style scoped>
.do-timeline-pane {
  width: 100%;
}
.do-timeline-header {
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
  cursor: pointer;
}
.swimlane-label:hover,
.swimlane-label:focus-visible {
  text-decoration: underline;
  outline: none;
}
.swimlane-bins {
  flex: 1;
  gap: 2px;
  overflow-x: auto;
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
