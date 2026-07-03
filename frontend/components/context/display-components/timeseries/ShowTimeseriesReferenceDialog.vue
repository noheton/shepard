<script setup lang="ts">
// PERF9 (2026-05-26): This component previously rendered all channels as
// unvirtualized v-checkbox rows. Fix: added a search filter (channelSearch +
// filteredTimeseries) that reduces the visible set at MFFD scale, plus a
// v-virtual-scroll path for the filtered list when >20 channels are visible
// and no rows are expanded (expanded rows have variable height incompatible
// with fixed-height virtual scroll — fall back to plain v-for in that case).
// Color indices stay tied to the original channel index so the chart legend
// stays in sync regardless of which filter is active.
// Filtering reuses channelMatchesSearch from ~/utils/timeseriesChannelFilter.ts
// (same utility used by the full-page timeseries reference view).
import type {
  ResponseError,
  SemanticAnnotation,
  Timeseries,
  TimeseriesEntity,
  TimeseriesReference,
  TimeseriesWithDataPoints,
} from "@dlr-shepard/backend-client";
import { useFetchTimeseries } from "~/composables/context/useFetchTimeseries";
import { useFetchTimeseriesPayload } from "~/composables/context/useFetchTimeseriesReferencePayload";
import { useFetchV2Channels } from "~/composables/container/useFetchV2Channels";
import type { Metrics } from "~/composables/context/useFetchTimeseriesReferencesMetrics";
import { useFetchTimeseriesReferenceMetrics } from "~/composables/context/useFetchTimeseriesReferencesMetrics";
import type { TimeseriesSeries } from "~/components/common/chart/types";
import { channelMatchesSearch } from "~/utils/timeseriesChannelFilter";

type TimeseriesMetrics = {
  metrics?: Metrics;
  annotations: SemanticAnnotation[];
  timeseriesObj?: TimeseriesEntity;
  shepardId?: string | null;
};

interface ShowTimeseriesReferenceDialogProps {
  collectionId: number;
  dataObjectId: number;
  timeseriesReferenceId: number;
  timeseriesContainerId: number;
  /** TS-ANNOT-V2: v2 container UUID — enables AnnotatedChannel when resolved */
  containerAppId?: string;
  timeseries: Timeseries[];
  timeseriesReference?: TimeseriesReference;
  isAllowedToEditCollection?: boolean;
}

const props = defineProps<ShowTimeseriesReferenceDialogProps>();
// TS-ANNOT-V2: build 5-tuple→shepardId map; resolves to null when containerAppId absent
const { resolveShepardId } = useFetchV2Channels(props.containerAppId ?? "");
const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

const timeseriesMetrics = ref<Record<string, TimeseriesMetrics>>({});

const metricsNames: Record<string, string> = {
  MIN: "Min",
  MAX: "Max",
  STDDEV: "Std dev",
  COUNT: "Count",
  MEAN: "Mean",
  MEDIAN: "Median",
  FIRST: "First",
  LAST: "Last",
  FREQUENCY: "Frequency",
};

const { timeseriesWithDataPoints, isLoading } = useFetchTimeseriesPayload(
  props.collectionId,
  props.dataObjectId,
  props.timeseriesReferenceId,
);

function getTimeseriesKey(ts: Timeseries): string {
  return `${ts.measurement}-${ts.device}-${ts.location}-${ts.symbolicName}-${ts.field}`;
}

function channelLabel(ts: Timeseries): string {
  const parts = [ts.device, ts.field, ts.location, ts.measurement, ts.symbolicName]
    .filter(Boolean);
  return parts.join(" · ");
}

// TS-ANNOT-V2: returns v2 AnnotatedChannel when shepardId resolved; falls back to
// v1 AnnotatedTimeseries (pre-TS-SEMANTIC-01 channels) or AnnotatedReference.
function annotatedFor(ts: Timeseries) {
  const m = timeseriesMetrics.value[getTimeseriesKey(ts)];
  if (props.containerAppId && m?.shepardId) {
    return new AnnotatedChannel(props.containerAppId, m.shepardId);
  }
  if (m?.timeseriesObj) {
    return new AnnotatedTimeseries(m.timeseriesObj);
  }
  return new AnnotatedReference(props.timeseriesReference?.appId ?? "", "TimeseriesReference");
}

watch(
  () => props.timeseries,
  async newTimeseries => {
    if (!newTimeseries || newTimeseries.length === 0) {
      timeseriesMetrics.value = {};
      return;
    }
    try {
      const data = await Promise.all(
        newTimeseries.map(async ts => {
          const metrics = await useFetchTimeseriesReferenceMetrics(
            props.collectionId,
            props.dataObjectId,
            props.timeseriesReferenceId,
            ts.measurement,
            ts.device,
            ts.location,
            ts.symbolicName,
            ts.field,
          );
          if (metrics?.COUNT && props.timeseriesReference) {
            const spanSeconds =
              (props.timeseriesReference.end - props.timeseriesReference.start) / 1e9;
            const freq = parseFloat(metrics.COUNT) / spanSeconds;
            metrics.FREQUENCY = `${toFormattedDouble(freq.toString(), 9)} Hz`;
          }

          const timeseriesObj = await useFetchTimeseries(
            props.timeseriesContainerId,
            ts.measurement,
            ts.device,
            ts.location,
            ts.symbolicName,
            ts.field,
          );

          const shepardId = resolveShepardId(ts.measurement, ts.device, ts.location, ts.symbolicName, ts.field);
          let annotations: SemanticAnnotation[] = [];
          if (shepardId && props.containerAppId) {
            // TS-ANNOT-V2: v2 path — channel has a shepardId from TS-SEMANTIC-01
            annotations = await new AnnotatedChannel(props.containerAppId, shepardId).fetchAnnotations().catch(() => []);
          } else if (timeseriesObj?.id) {
            // TS-ANNOT-V2: v1 fallback — channel predates TS-SEMANTIC-01 (see aidocs/16)
            annotations = await new AnnotatedTimeseries(timeseriesObj).fetchAnnotations().catch(() => []);
          }

          return [getTimeseriesKey(ts), { metrics, annotations, timeseriesObj, shepardId }] as const;
        }),
      );
      timeseriesMetrics.value = Object.fromEntries(data);
    } catch (error) {
      handleError(error as ResponseError, "fetching timeseries metrics");
      timeseriesMetrics.value = {};
    }
  },
  { immediate: true },
);

// ── chart series ──────────────────────────────────────────────────────────────

const chartColors: string[] = [
  "#7ECA8F", "#FCA54D", "#B799DB", "#E56874",
  "#4097CC", "#FFD145", "#8C8C8C", "#F06292",
];

function getColor(index: number): string {
  return chartColors[index % chartColors.length] ?? "#8C8C8C";
}

// channels enabled in chart — default all on
const enabledKeys = ref<Set<string>>(new Set());
watch(
  () => props.timeseries,
  ts => {
    enabledKeys.value = new Set(ts.map(getTimeseriesKey));
  },
  { immediate: true },
);

function toggleChannel(key: string) {
  if (enabledKeys.value.has(key)) {
    enabledKeys.value.delete(key);
  } else {
    enabledKeys.value.add(key);
  }
  // trigger reactivity on Set mutation
  enabledKeys.value = new Set(enabledKeys.value);
}

function mapToSeries(items: TimeseriesWithDataPoints[]): TimeseriesSeries[] {
  return items
    .map((item, idx) => {
      const key = getTimeseriesKey(item.timeseries);
      return {
        key,
        name: channelLabel(item.timeseries),
        color: getColor(idx),
        data: item.points.map(p => [p.timestamp, p.value] as [number, number]),
      };
    })
    .filter(s => enabledKeys.value.has(s.key));
}

const chartSeries = computed<TimeseriesSeries[]>(() => {
  if (!timeseriesWithDataPoints.value) return [];
  const filtered = timeseriesWithDataPoints.value.filter(item =>
    props.timeseries.some(
      ts =>
        ts.device === item.timeseries.device &&
        ts.field === item.timeseries.field &&
        ts.location === item.timeseries.location &&
        ts.measurement === item.timeseries.measurement &&
        ts.symbolicName === item.timeseries.symbolicName,
    ),
  );
  return mapToSeries(filtered);
});

// ── expanded metrics rows ─────────────────────────────────────────────────────
const expandedMetrics = ref<Set<string>>(new Set());
function toggleMetrics(key: string) {
  if (expandedMetrics.value.has(key)) expandedMetrics.value.delete(key);
  else expandedMetrics.value.add(key);
  expandedMetrics.value = new Set(expandedMetrics.value);
}
const allMetricsExpanded = computed(() =>
  props.timeseries.length > 0 &&
  props.timeseries.every((ts: Timeseries) => expandedMetrics.value.has(getTimeseriesKey(ts))),
);
function toggleAllMetrics() {
  if (allMetricsExpanded.value) {
    expandedMetrics.value = new Set();
  } else {
    expandedMetrics.value = new Set(props.timeseries.map(getTimeseriesKey));
  }
}

// ── channel search / filter ───────────────────────────────────────────────────

/** Search query typed by the user to narrow the channel list. */
const channelSearch = ref("");

/**
 * Timeseries rows visible after applying channelSearch.
 * Each entry carries the original channel index so color-swatch assignments
 * remain stable regardless of which subset the filter produces.
 */
const filteredTimeseries = computed<Array<{ ts: Timeseries; originalIdx: number }>>(() =>
  props.timeseries
    .map((ts, idx) => ({ ts, originalIdx: idx }))
    .filter(({ ts }) => channelMatchesSearch(ts, channelSearch.value)),
);

/**
 * When filteredTimeseries.length > 20 AND no rows are currently expanded,
 * we use v-virtual-scroll (fixed 52px row height). If any row is expanded
 * the height varies — fall back to a plain v-for in that case.
 */
const useVirtualScroll = computed(
  () => filteredTimeseries.value.length > 20 && expandedMetrics.value.size === 0,
);
</script>

<template>
  <v-dialog
    v-if="timeseriesReference"
    v-model="showDialog"
    persistent
    :max-width="1080"
  >
    <v-card>
      <template #title>
        <div class="d-flex justify-space-between align-baseline">
          <div class="d-flex align-center ga-2">
            <div class="text-h4">Graph &amp; Metrics</div>
            <!-- AI1c — quality score chip for this reference -->
            <QualityScoreChip
              v-if="timeseriesReference"
              :score="timeseriesReference.qualityScore"
            />
          </div>
          <v-btn
            variant="plain"
            density="compact"
            icon="mdi-close"
            @click="showDialog = false"
          />
        </div>
      </template>

      <template #text>
        <!-- chart ─────────────────────────────────────────────────────────── -->
        <div class="mb-4" style="position: relative">
          <CenteredLoadingSpinner v-if="isLoading" />
          <TimeseriesChart
            v-else
            :series="chartSeries"
            :x-min="timeseriesReference ? timeseriesReference.start / 1e6 : undefined"
            :x-max="timeseriesReference ? timeseriesReference.end / 1e6 : undefined"
            show-legend
            height="340px"
          />
        </div>

        <!-- channel list ─────────────────────────────────────────────────── -->
        <div class="d-flex align-center mb-2">
          <span class="text-subtitle-1">Channels</span>
          <v-spacer />
          <v-btn
            v-if="timeseries.length > 0"
            density="compact"
            variant="text"
            size="small"
            :append-icon="allMetricsExpanded ? 'mdi-chevron-up' : 'mdi-chevron-down'"
            @click="toggleAllMetrics"
          >{{ allMetricsExpanded ? 'Collapse all' : 'Expand all' }}</v-btn>
        </div>

        <!-- PERF9: search filter — reduces visible rows at MFFD scale (200+ channels) -->
        <v-text-field
          v-if="timeseries.length > 10"
          v-model="channelSearch"
          clearable
          density="compact"
          hide-details
          placeholder="Filter channels…"
          prepend-inner-icon="mdi-magnify"
          variant="outlined"
          class="mb-2"
        />
        <div
          v-if="filteredTimeseries.length === 0 && channelSearch"
          class="text-body-2 text-medium-emphasis mb-2"
        >
          No channels match "{{ channelSearch }}"
        </div>

        <!-- PERF9: virtual-scroll path (>20 rows, none expanded) -->
        <v-virtual-scroll
          v-if="useVirtualScroll"
          :items="filteredTimeseries"
          :item-height="52"
          :max-height="420"
        >
          <template #default="{ item: { ts, originalIdx } }">
            <div
              :key="getTimeseriesKey(ts)"
              class="channel-row mb-1"
            >
              <div class="d-flex align-center">
                <div
                  class="color-swatch mr-2 flex-shrink-0"
                  :style="{ background: getColor(originalIdx) }"
                />
                <v-checkbox
                  :model-value="enabledKeys.has(getTimeseriesKey(ts))"
                  density="compact"
                  hide-details
                  class="flex-shrink-0 mr-1"
                  @update:model-value="toggleChannel(getTimeseriesKey(ts))"
                />
                <span
                  class="text-body-2 flex-grow-1"
                  style="cursor: pointer"
                  @click="toggleChannel(getTimeseriesKey(ts))"
                >{{ channelLabel(ts) }}</span>
                <v-btn
                  density="compact"
                  variant="text"
                  size="small"
                  :icon="expandedMetrics.has(getTimeseriesKey(ts)) ? 'mdi-chevron-up' : 'mdi-chevron-down'"
                  @click="toggleMetrics(getTimeseriesKey(ts))"
                />
              </div>
            </div>
          </template>
        </v-virtual-scroll>

        <!-- plain v-for path: ≤20 rows OR at least one row is expanded -->
        <template v-else>
          <div
            v-for="{ ts, originalIdx } in filteredTimeseries"
            :key="getTimeseriesKey(ts)"
            class="channel-row mb-1"
          >
            <!-- header row: color swatch + checkbox + label + expand metrics -->
            <div class="d-flex align-center">
              <div
                class="color-swatch mr-2 flex-shrink-0"
                :style="{ background: getColor(originalIdx) }"
              />
              <v-checkbox
                :model-value="enabledKeys.has(getTimeseriesKey(ts))"
                density="compact"
                hide-details
                class="flex-shrink-0 mr-1"
                @update:model-value="toggleChannel(getTimeseriesKey(ts))"
              />
              <span
                class="text-body-2 flex-grow-1"
                style="cursor: pointer"
                @click="toggleChannel(getTimeseriesKey(ts))"
              >{{ channelLabel(ts) }}</span>
              <v-btn
                density="compact"
                variant="text"
                size="small"
                :icon="expandedMetrics.has(getTimeseriesKey(ts)) ? 'mdi-chevron-up' : 'mdi-chevron-down'"
                @click="toggleMetrics(getTimeseriesKey(ts))"
              />
            </div>

            <!-- expanded: metrics grid + annotations -->
            <div
              v-if="expandedMetrics.has(getTimeseriesKey(ts))"
              class="ml-10 mt-1 mb-2"
            >
              <div class="metrics-grid text-body-2 text-medium-emphasis mb-2">
                <template
                  v-for="(val, key) in timeseriesMetrics[getTimeseriesKey(ts)]?.metrics"
                  :key="key"
                >
                  <span class="metric-label">{{ metricsNames[key] ?? key }}:</span>
                  <span class="metric-value">{{ val }}</span>
                </template>
              </div>
              <div class="d-flex flex-wrap gap-1">
                <SemanticAnnotationChip
                  v-for="annotation in timeseriesMetrics[getTimeseriesKey(ts)]?.annotations"
                  :key="annotation.id"
                  :can-delete="!!isAllowedToEditCollection"
                  :annotation="annotation"
                  :annotated-type="annotatedFor(ts)"
                />
              </div>
            </div>
          </div>
        </template>

        <div class="d-flex justify-end mt-4">
          <v-btn
            variant="flat"
            color="treeview"
            text="Close"
            @click="showDialog = false"
          />
        </div>
      </template>
    </v-card>
  </v-dialog>
</template>

<style scoped>
.color-swatch {
  width: 14px;
  height: 14px;
  border-radius: 3px;
}

.metrics-grid {
  display: grid;
  grid-template-columns: max-content 1fr;
  column-gap: 8px;
  row-gap: 2px;
}

.metric-label {
  font-weight: 500;
  white-space: nowrap;
}
</style>
