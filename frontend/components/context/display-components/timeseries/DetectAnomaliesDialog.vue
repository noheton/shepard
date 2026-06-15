<script setup lang="ts">
/**
 * AI1b — parametrized anomaly detection dialog.
 *
 * Opens from the "Detect anomalies" button on the TimeseriesReference detail
 * page. Lets the user tune window size / threshold / createAnnotations, calls
 * POST /v2/references/{appId}/detect-anomalies, and displays the
 * per-interval result table inline.
 */
import {
  useTimeseriesReferenceAnnotations,
  type AnomalyDetectionResultDto,
  type AnomalyIntervalDto,
} from "~/composables/context/useTimeseriesReferenceAnnotations";

const props = defineProps<{
  /** appId of the timeseries reference to run detection on. */
  refAppId: string;
}>();

/**
 * Emitted after a successful run with createAnnotations=true so the parent
 * page can refresh its own annotation list.
 */
const emit = defineEmits<{
  annotationsSaved: [];
}>();

const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

// ── form state ────────────────────────────────────────────────────────────────

const windowSize = ref<number>(51);
const threshold = ref<number>(6.0);
const createAnnotations = ref<boolean>(false);

// ── detection state ───────────────────────────────────────────────────────────

const refAppIdRef = computed(() => props.refAppId);
// We only need detectAnomalies + detecting from this composable instance.
// The parent page has its own instance that manages the visible annotation list.
const { detecting, detectAnomalies } = useTimeseriesReferenceAnnotations(refAppIdRef);

const result = ref<AnomalyDetectionResultDto | null>(null);
const errorMessage = ref<string | null>(null);

// ── run ───────────────────────────────────────────────────────────────────────

async function runDetection() {
  result.value = null;
  errorMessage.value = null;
  const res = await detectAnomalies({
    window: windowSize.value,
    k: threshold.value,
    createAnnotations: createAnnotations.value,
  });
  if (res) {
    result.value = res;
    if (createAnnotations.value && res.annotationsCreated > 0) {
      emit("annotationsSaved");
    }
  } else {
    errorMessage.value = "Detection failed. Check the browser console for details.";
  }
}

// ── helpers ───────────────────────────────────────────────────────────────────

function formatNs(ns: number): string {
  const ms = ns / 1_000_000;
  return new Date(ms).toISOString().replace("T", " ").slice(0, 19) + " UTC";
}

function formatInterval(interval: AnomalyIntervalDto): string {
  const start = formatNs(interval.startNs);
  if (interval.startNs === interval.endNs) return `${start} (point)`;
  const end = formatNs(interval.endNs);
  return `${start}  →  ${end}`;
}

const tableHeaders = [
  { title: "Interval", key: "range", sortable: false },
  { title: "Peak value", key: "peakValue", sortable: true, align: "end" as const },
  { title: "Max |z|", key: "maxZScore", sortable: true, align: "end" as const },
];

function closeDialog() {
  showDialog.value = false;
  result.value = null;
  errorMessage.value = null;
}
</script>

<template>
  <v-dialog
    v-model="showDialog"
    :max-width="700"
    persistent
  >
    <v-card>
      <template #title>
        <div class="d-flex justify-space-between align-center">
          <div class="d-flex align-center ga-2">
            <v-icon icon="mdi-magnify-scan" />
            <span>Detect anomalies</span>
          </div>
          <v-btn
            variant="plain"
            density="compact"
            icon="mdi-close"
            aria-label="Close dialog"
            @click="closeDialog"
          />
        </div>
      </template>

      <template #text>
        <!-- ── Parameters ─────────────────────────────────────────────────── -->
        <div class="text-subtitle-2 mb-2">Detection parameters</div>
        <v-row dense>
          <v-col cols="12" sm="6">
            <v-text-field
              v-model.number="windowSize"
              label="Window size"
              type="number"
              :min="3"
              :step="2"
              density="compact"
              variant="outlined"
              hint="Rolling window for median (odd, ≥ 3). Default: 51."
              persistent-hint
            />
          </v-col>
          <v-col cols="12" sm="6">
            <v-text-field
              v-model.number="threshold"
              label="Threshold k"
              type="number"
              :step="0.5"
              density="compact"
              variant="outlined"
              hint="Points with |z| > k are flagged. Higher = fewer detections. Default: 6.0."
              persistent-hint
            />
          </v-col>
        </v-row>
        <v-checkbox
          v-model="createAnnotations"
          label="Save results as annotations"
          density="compact"
          hide-details
          class="mt-3 mb-1"
        />
        <div class="text-caption text-medium-emphasis mb-4">
          When enabled, detected intervals are persisted as TimeseriesAnnotation
          nodes and appear in the "Anomalies &amp; intervals" section below.
        </div>

        <!-- ── Error ──────────────────────────────────────────────────────── -->
        <v-alert
          v-if="errorMessage"
          type="error"
          variant="tonal"
          class="mb-4"
          closable
          @click:close="errorMessage = null"
        >
          {{ errorMessage }}
        </v-alert>

        <!-- ── Results ────────────────────────────────────────────────────── -->
        <template v-if="result">
          <v-divider class="mb-4" />

          <div class="d-flex align-center ga-2 mb-3">
            <v-icon
              :icon="result.anomalies.length > 0 ? 'mdi-alert-circle-outline' : 'mdi-check-circle-outline'"
              :color="result.anomalies.length > 0 ? 'warning' : 'success'"
              size="20"
            />
            <span class="text-subtitle-2">
              {{ result.anomalies.length === 0
                ? "No anomalies detected — series looks clean."
                : `${result.anomalies.length} anomalous interval(s) found` }}
            </span>
            <v-spacer />
            <span class="text-caption text-medium-emphasis">
              {{ result.totalPoints.toLocaleString() }} points evaluated
              · window {{ result.windowSize }}
              · k = {{ result.threshold }}
            </span>
          </div>

          <div
            v-if="result.anomalies.length > 0"
            style="overflow-x: auto"
          >
            <v-data-table
              :headers="tableHeaders"
              :items="result.anomalies"
              :items-per-page="10"
              density="compact"
            >
              <template #[`item.range`]="{ item }">
                <span class="text-mono text-caption">{{ formatInterval(item) }}</span>
              </template>
              <template #[`item.peakValue`]="{ item }">
                <span class="text-mono">{{ item.peakValue.toPrecision(6) }}</span>
              </template>
              <template #[`item.maxZScore`]="{ item }">
                <v-chip
                  size="x-small"
                  :color="item.maxZScore > 10 ? 'error' : item.maxZScore > threshold ? 'warning' : 'default'"
                  variant="tonal"
                >
                  {{ item.maxZScore.toFixed(2) }}
                </v-chip>
              </template>
            </v-data-table>
          </div>

          <div
            v-if="result.annotationsCreated > 0"
            class="text-caption text-medium-emphasis mt-2"
          >
            {{ result.annotationsCreated }} annotation(s) saved to this reference.
          </div>
        </template>
      </template>

      <template #actions>
        <v-spacer />
        <v-btn
          variant="text"
          @click="closeDialog"
        >
          Close
        </v-btn>
        <v-btn
          color="primary"
          variant="flat"
          prepend-icon="mdi-magnify-scan"
          :loading="detecting"
          :disabled="detecting"
          @click="runDetection"
        >
          Run detection
        </v-btn>
      </template>
    </v-card>
  </v-dialog>
</template>

<style scoped>
.text-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-variant-numeric: tabular-nums;
}
</style>
