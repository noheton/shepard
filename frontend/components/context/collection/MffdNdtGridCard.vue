<script lang="ts" setup>
/**
 * MFFD-NDT-GRID-1 — 14x14 thermography campaign coverage widget.
 *
 * Visualises the MFFD upper-shell OTvis thermography campaign as a
 * Section x Module heat-map. Each cell corresponds to one (S<n>, M<n>)
 * tile on the upper-shell surface; cell shading encodes the number
 * of measurements landed in that cell, a red border flags any cell
 * that contains a NOK qsClassification.
 *
 * Data flow:
 *   1. Fetch all DataObjects in the collection (cached via
 *      useFetchDataObjectMapByCollection-style pattern — here we use
 *      the API directly because we need the full DO list, not just
 *      the id->name map; the existing composable only exposes the
 *      latter).
 *   2. For each DO, fetch its SemanticAnnotation list.
 *   3. Bucket by (section, module) via the pure helpers in
 *      `utils/mffdNdtGrid.ts`.
 *   4. Render the 14x14 grid; emit `select` on cell click.
 *
 * Concurrency: per-DO annotation fetches are run in chunks of
 * MAX_PARALLEL_FETCHES to avoid swamping the backend on a
 * collection with hundreds of DOs. A failed fetch on one DO is
 * silently skipped (treated as no annotations) so a transient error
 * doesn't blank the widget.
 *
 * Conditional render: the parent page checks
 * `useMffdNdtGridProbe` (defined here) for a cheap "any DO in the
 * collection carries the section predicate?" probe before mounting
 * this component. The probe samples the first batch only -- if
 * thermography data is present anywhere in the collection it's
 * effectively certain to be in the first batch, since OTvis uploads
 * tend to dominate any collection that has them.
 *
 * Helpers live in `utils/mffdNdtGrid.ts` and are unit-tested in
 * `tests/unit/mffdNdtGrid.test.ts` (30 cases).
 */
import {
  DataObjectApi,
  SemanticAnnotationApi,
  type DataObject,
  type SemanticAnnotation,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import { useAdvancedMode } from "~/composables/context/useAdvancedMode";
import {
  EMPTY_CELL_COLOUR,
  annotationsContainSection,
  bucketByGrid,
  cellKey,
  colourForCount,
  enumerateGrid,
  formatTooltip,
  maxMeasurementCount,
  type DataObjectWithAnnotations,
  type GridCellData,
} from "~/utils/mffdNdtGrid";

const props = defineProps<{
  collectionId: number;
}>();

const emit = defineEmits<{
  (
    e: "select",
    payload: { section: string; module: string; dataObjects: DataObject[] },
  ): void;
}>();

// Cap parallel annotation fetches so we don't trigger backend
// connection-pool exhaustion on a large collection.
const MAX_PARALLEL_FETCHES = 8;

const dataObjectApi = useShepardApi(DataObjectApi);
const annotationApi = useShepardApi(SemanticAnnotationApi);

const buckets = ref<Map<string, GridCellData>>(new Map());
const maxCount = ref(0);
const isLoading = ref(true);
const hasError = ref(false);
const dataObjectsById = ref<Map<number, DataObject>>(new Map());

const { advancedMode } = useAdvancedMode();

async function fetchAll(): Promise<void> {
  isLoading.value = true;
  hasError.value = false;
  try {
    const dos = await dataObjectApi.value.getAllDataObjects({
      collectionId: props.collectionId,
    });
    const idMap = new Map<number, DataObject>();
    for (const d of dos) idMap.set(d.id, d);
    dataObjectsById.value = idMap;

    // Chunked per-DO annotation fetches.
    const withAnn: DataObjectWithAnnotations[] = [];
    for (let i = 0; i < dos.length; i += MAX_PARALLEL_FETCHES) {
      const slice = dos.slice(i, i + MAX_PARALLEL_FETCHES);
      const results = await Promise.allSettled(
        slice.map(d =>
          annotationApi.value.getAllDataObjectAnnotations({
            collectionId: props.collectionId,
            dataObjectId: d.id,
          }),
        ),
      );
      for (let j = 0; j < slice.length; j++) {
        const d = slice[j];
        const r = results[j];
        if (!d || !r) continue;
        const annotations: SemanticAnnotation[] =
          r.status === "fulfilled" ? r.value : [];
        withAnn.push({ id: d.id, name: d.name, annotations });
      }
    }

    const bucketed = bucketByGrid(withAnn);
    buckets.value = bucketed;
    maxCount.value = maxMeasurementCount(bucketed);
  } catch {
    hasError.value = true;
  } finally {
    isLoading.value = false;
  }
}

onMounted(() => {
  void fetchAll();
});

watch(
  () => props.collectionId,
  () => {
    void fetchAll();
  },
);

const totalMeasurements = computed(() => {
  let total = 0;
  for (const cell of buckets.value.values()) {
    total += cell.measurements.length;
  }
  return total;
});

const coveredCells = computed(() => buckets.value.size);

const failedCells = computed(() => {
  let n = 0;
  for (const cell of buckets.value.values()) {
    if (cell.anyFailed) n++;
  }
  return n;
});

const cells = computed(() => enumerateGrid(14, 14));

function cellFor(section: string, module: string): GridCellData | null {
  return buckets.value.get(cellKey(section, module)) ?? null;
}

function bgFor(section: string, module: string): string {
  const c = cellFor(section, module);
  if (!c) return EMPTY_CELL_COLOUR;
  return colourForCount(c.measurements.length, maxCount.value);
}

function tooltipFor(section: string, module: string): string {
  const c = cellFor(section, module);
  if (!c) return `Section ${section} · Module ${module} · no measurements`;
  return formatTooltip(c);
}

function onCellClick(section: string, module: string): void {
  const c = cellFor(section, module);
  const dos: DataObject[] = c
    ? c.measurements
        .map(m => dataObjectsById.value.get(m.dataObject.id))
        .filter((d): d is DataObject => d !== undefined)
    : [];
  emit("select", { section, module, dataObjects: dos });
}

const isEmpty = computed(() => !isLoading.value && buckets.value.size === 0);
</script>

<template>
  <v-card
    class="mffd-ndt-grid-card"
    variant="outlined"
    data-testid="mffd-ndt-grid-card"
  >
    <v-card-title class="d-flex align-center ga-2 flex-wrap">
      <v-icon size="small" color="primary">mdi-grid</v-icon>
      <span>NDT Campaign Coverage — 14 × 14 grid</span>
      <v-spacer />
      <v-chip
        v-if="!isLoading && !hasError && !isEmpty"
        size="small"
        variant="tonal"
        color="primary"
        data-testid="mffd-ndt-grid-summary"
      >
        {{ coveredCells }} / 196 cells · {{ totalMeasurements }} measurements
      </v-chip>
      <v-chip
        v-if="failedCells > 0"
        size="small"
        variant="flat"
        color="error"
        data-testid="mffd-ndt-grid-failed-chip"
      >
        <v-icon start size="small">mdi-alert-octagon</v-icon>
        {{ failedCells }} failed
      </v-chip>
    </v-card-title>

    <v-card-text>
      <CenteredLoadingSpinner v-if="isLoading" />
      <v-alert
        v-else-if="hasError"
        type="warning"
        variant="tonal"
        density="compact"
        data-testid="mffd-ndt-grid-error"
      >
        Could not load thermography coverage data.
      </v-alert>
      <v-alert
        v-else-if="isEmpty"
        type="info"
        variant="tonal"
        density="compact"
        data-testid="mffd-ndt-grid-empty"
      >
        No NDT campaign data found. Upload <code>.OTvis</code> files to
        populate the coverage grid.
      </v-alert>
      <div v-else class="grid-wrapper">
        <div
          class="ndt-grid"
          :class="{ 'ndt-grid-narrow': false }"
          role="grid"
          aria-label="MFFD upper-shell thermography 14 by 14 coverage grid"
        >
          <button
            v-for="(c, idx) in cells"
            :key="`${c.section}-${c.module}-${idx}`"
            type="button"
            class="ndt-cell"
            :class="{
              'ndt-cell-empty':
                (cellFor(c.section, c.module)?.measurements.length ?? 0) === 0,
              'ndt-cell-failed': cellFor(c.section, c.module)?.anyFailed,
            }"
            :style="{ backgroundColor: bgFor(c.section, c.module) }"
            :title="tooltipFor(c.section, c.module)"
            :data-testid="`ndt-cell-${c.section}-${c.module}`"
            :aria-label="tooltipFor(c.section, c.module)"
            @click="onCellClick(c.section, c.module)"
          >
            <span class="ndt-cell-label">{{ c.section }}·{{ c.module }}</span>
            <span
              v-if="
                advancedMode &&
                (cellFor(c.section, c.module)?.layers.length ?? 0) > 0
              "
              class="ndt-cell-layers"
              data-testid="ndt-cell-layers"
            >
              {{ cellFor(c.section, c.module)?.layers.join(",") }}
            </span>
          </button>
        </div>
      </div>
    </v-card-text>
  </v-card>
</template>

<style lang="scss" scoped>
.mffd-ndt-grid-card {
  margin-bottom: 24px;
}

.grid-wrapper {
  overflow-x: auto;
}

.ndt-grid {
  display: grid;
  grid-template-columns: repeat(14, minmax(36px, 1fr));
  grid-auto-rows: 36px;
  gap: 2px;
  min-width: 560px;
  user-select: none;
}

.ndt-cell {
  border: 1px solid rgba(0, 0, 0, 0.12);
  border-radius: 2px;
  padding: 0;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  font: inherit;
  color: inherit;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  line-height: 1.1;
  background: transparent;
  transition: transform 80ms ease;
}

.ndt-cell:hover,
.ndt-cell:focus-visible {
  transform: scale(1.06);
  z-index: 1;
  outline: 2px solid rgba(var(--v-theme-primary), 0.85);
  outline-offset: 1px;
}

.ndt-cell-empty {
  cursor: default;
}

.ndt-cell-failed {
  border: 2px solid rgb(var(--v-theme-error));
}

.ndt-cell-label {
  font-size: 8px;
  font-weight: 500;
  color: rgba(0, 0, 0, 0.72);
  // Add a subtle white shadow so labels stay readable on dark heat-map cells.
  text-shadow: 0 0 2px rgba(255, 255, 255, 0.55);
}

.ndt-cell-layers {
  font-size: 7px;
  color: rgba(0, 0, 0, 0.7);
  text-shadow: 0 0 2px rgba(255, 255, 255, 0.6);
  margin-top: 1px;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

// Narrow-viewport rule -- below 800px we drop the per-cell labels but
// keep the colour-map grid intact.
@media (max-width: 800px) {
  .ndt-cell-label,
  .ndt-cell-layers {
    display: none;
  }
  .ndt-grid {
    grid-template-columns: repeat(14, minmax(20px, 1fr));
    grid-auto-rows: 20px;
    min-width: 320px;
  }
}
</style>
