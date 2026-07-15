<script setup lang="ts">
import type {
  ResponseError,
  SemanticAnnotation,
  Timeseries,
  TimeseriesWithDataPoints,
} from "@dlr-shepard/backend-client";
import {
  ContainersApi,
  ReferencesApi,
} from "@dlr-shepard/backend-client";
import type { TimeseriesSeries } from "~/components/common/chart/types";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";
import { useFetchReferenceV2 } from "~/composables/context/useFetchReferenceV2";
import { useTimeseriesReferenceAnnotations } from "~/composables/context/useTimeseriesReferenceAnnotations";
import { channelMatchesSearch, filterChannelsBySelection } from "~/utils/timeseriesChannelFilter";
import { MAX_CHANNEL_PAGE_SIZE } from "~/utils/channelConstants";

definePageMeta({ layout: "collection" });

// V2CONV — client-side channel summary metrics, computed from the bulk data
// points the page already fetches. Replaces the v1 per-row metrics N+1.
interface ChannelMetrics {
  COUNT?: number;
  MIN?: number;
  MAX?: number;
  MEAN?: number;
}

interface TimeseriesDataTableItem extends Timeseries {
  isSelected: boolean;
  // Per-channel summary metrics, computed client-side from bulk points.
  metrics?: ChannelMetrics;
  metricsLoading?: boolean;
  // Per-channel semantic annotations — no appId-keyed v2 source yet, so this
  // column renders "—" until a /v2 per-channel annotation endpoint exists
  // (tracked as REF-EDIT-TS-CHANNEL-ANN in aidocs/16).
  annotations?: SemanticAnnotation[];
  annotationsLoading?: boolean;
}

// The normalised view this page renders. V2CONV: sourced entirely from the
// unified GET /v2/references/{appId} entity (kind-agnostic envelope + the
// per-kind `payload` map) — no numeric Neo4j id is needed or used. This is
// the structural fix for the L2 eternal-spinner class: the previous page
// gated render + every sub-fetch on `referenceV2.id`, which is @JsonIgnore-d
// on the wire and therefore always undefined.
interface TsRefView {
  appId: string;
  id: number;
  name: string;
  createdAt: Date;
  createdBy: string;
  updatedAt: Date | null;
  updatedBy: string | null;
  start: number;
  end: number;
  timeseries: Timeseries[];
  timeseriesContainerId?: number;
  timeseriesContainerAppId?: string;
  referencedContainerName?: string;
  referencedContainerAvailability?: "available" | "deleted" | "forbidden" | "error";
  timeReference?: string;
  wallClockOffset?: number | null;
  wallClockOffsetSource?: string | null;
  qualityScore?: number | null;
}

const MaxSelectableItems = 7;

const { routeParams } = useCollectionRouteParams();
const collectionIdStr = routeParams.value.collectionId ?? "";
const dataObjectIdStr = routeParams.value.dataObjectId ?? "";

const { collection, isAllowedToEditCollection } =
  useFetchCollection(collectionIdStr);
const { dataObject } = useFetchDataObject(collectionIdStr, dataObjectIdStr);

// V2CONV / UX612-C1: the route param IS the v2 appId (frontend-v2-only rule).
// Load the reference via GET /v2/references/{appId} and drive the whole page
// off the returned envelope + payload. No numeric-id resolution — that path
// is what spun forever (referenceV2.id is @JsonIgnore-d on the wire).
const { referenceV2, notFound: referenceNotFound, refresh: refreshReferenceV2 } =
  useFetchReferenceV2(() => routeParams.value.timeseriesReferenceId);

const timeseriesReference = computed<TsRefView | undefined>(() => {
  const r = referenceV2.value;
  if (!r) return undefined;
  const p = (r.payload ?? {}) as Record<string, unknown>;
  return {
    appId: r.appId ?? routeParams.value.timeseriesReferenceId ?? "",
    // Numeric ref id is @JsonIgnore-d on /v2; 0 is a display-only placeholder
    // (only shown in advanced mode). The page never keys anything off it.
    id: typeof r.id === "number" ? r.id : 0,
    name: r.name,
    createdAt: r.createdAt,
    createdBy: r.createdBy,
    updatedAt: r.updatedAt,
    updatedBy: r.updatedBy,
    start: Number(p.start ?? 0),
    end: Number(p.end ?? 0),
    timeseries: (p.timeseries as Timeseries[] | undefined) ?? [],
    timeseriesContainerId:
      p.timeseriesContainerId != null ? Number(p.timeseriesContainerId) : undefined,
    timeseriesContainerAppId: (p.timeseriesContainerAppId as string | undefined) ?? undefined,
    timeReference: (p.timeReference as string | undefined) ?? undefined,
    // APISIMP-TSREF-WALLCLOCK-OFFSET-NANOS: v2 wire now emits ISO 8601 string; parse to ns.
    wallClockOffset: (() => {
      const wco = p.wallClockOffset;
      if (wco == null) return null;
      if (typeof wco === "number") return wco;
      const ms = new Date(wco as string).getTime();
      return isNaN(ms) ? null : ms * 1_000_000;
    })(),
    wallClockOffsetSource: (p.wallClockOffsetSource as string | undefined) ?? null,
    qualityScore: (p.qualityScore as number | undefined) ?? null,
  };
});

// TM1a — refresh the reference after a time-reference patch so the panel
// shows the updated mode / offset immediately.
async function onTimeReferenceUpdated() {
  refreshReferenceV2();
}

// TA1a + AI1b — the reference appId (the only identity this page needs).
const timeseriesReferenceAppId = computed<string | undefined>(
  () => timeseriesReference.value?.appId,
);
const {
  annotations: tsAnnotations,
  loading: tsAnnotationsLoading,
  deleteAnnotation: deleteTsAnnotation,
  fetchAll: refetchTsAnnotations,
} = useTimeseriesReferenceAnnotations(timeseriesReferenceAppId);

function formatTsAnnotationRange(ann: { startNs: number; endNs: number }): string {
  const startMs = ann.startNs / 1_000_000;
  const endMs = ann.endNs / 1_000_000;
  const startStr = new Date(startMs).toISOString().replace("T", " ").slice(0, 19);
  const endStr = new Date(endMs).toISOString().replace("T", " ").slice(0, 19);
  if (startMs === endMs) return `${startStr} UTC (point)`;
  return `${startStr}  →  ${endStr} UTC`;
}

const timeseriesDataTableItems = ref<TimeseriesDataTableItem[]>([]);
const numberOfSelectedItems = ref<number>(0);
const showDeleteDialog       = ref<boolean>(false);
const showEditDialog          = ref<boolean>(false);
const showVisualize3D        = ref<boolean>(false);
const showDetectAnomalies    = ref<boolean>(false);
const canVisualize3D = computed(
  () =>
    (timeseriesReference.value?.timeseries?.length ?? 0) >= 3 &&
    !!timeseriesReference.value?.timeseriesContainerId,
);

// ── V2CONV: appId-keyed channel listing + bulk data ──────────────────────
// The /channels endpoint is appId-keyed. The container appId comes from the
// v2 reference payload (UX612-C1). Matching the reference's 5-tuple channels
// against the container's channel shepardIds lets us pull all points in one
// bulk POST — no numeric-id path, which is what spun forever (L2).
interface ChannelV2 {
  shepardId: string;
  measurement?: string;
  device?: string;
  field?: string;
  location?: string;
  symbolicName?: string;
}
const channelsV2 = ref<ChannelV2[]>([]);
const containerAppId = computed<string | undefined>(
  () =>
    timeseriesReference.value?.timeseriesContainerAppId ??
    (referenceV2.value?.payload?.timeseriesContainerAppId as string | undefined),
);
const channelListingApi = useV2ShepardApi(ContainersApi);

// Resolved container name — fetched from GET /v2/containers/{appId} so the
// header shows the human-readable name rather than "unknown name (ID: …)".
const containerName = ref<string | undefined>(undefined);

// Chart payload — the bulk points feeding the Channel Overview chart and the
// client-side per-channel metrics.
const chartPayload = ref<TimeseriesWithDataPoints[] | undefined>(undefined);
const chartPayloadLoading = ref<boolean>(false);
const chartPayloadFetched = computed(() => chartPayload.value !== undefined);

// Channel Overview series — shows all channels when none are selected;
// filters to checked channels when ≥1 box is ticked.
const overviewSeries = computed<TimeseriesSeries[]>(() => {
  if (!chartPayload.value) return [];
  const selectedKeys = new Set(
    timeseriesDataTableItems.value
      .filter(item => item.isSelected)
      .map(item => timeseriesKey(item)),
  );
  const source = filterChannelsBySelection(
    chartPayload.value,
    selectedKeys,
    p => timeseriesKey(p.timeseries),
  );
  return source.map((p, idx) => ({
    key: timeseriesKey(p.timeseries),
    name: channelLabel(p.timeseries),
    color: getColor(idx),
    data: p.points.map(pt => [pt.timestamp, pt.value] as [number, number]),
  }));
});

// Reference time bounds in milliseconds — used to lock the chart to exactly
// the referenced data window (start/end are nanoseconds in the API).
const overviewXMin = computed<number | undefined>(() =>
  timeseriesReference.value ? timeseriesReference.value.start / 1e6 : undefined,
);
const overviewXMax = computed<number | undefined>(() =>
  timeseriesReference.value ? timeseriesReference.value.end / 1e6 : undefined,
);

// TM1b — wall-clock overlay: pass the offset to the chart when the reference
// is EXPERIMENT_RELATIVE and wallClockOffset is set so the tooltip shows
// both relative ("t+8.234s") and absolute ("2024-06-02 14:30:08.234 UTC") labels.
const overviewWallClockOffsetMs = computed<number | undefined>(() => {
  const ref = timeseriesReference.value;
  if (
    ref?.timeReference === "EXPERIMENT_RELATIVE" &&
    ref.wallClockOffset != null
  ) {
    return ref.wallClockOffset / 1_000_000;
  }
  return undefined;
});

const chartColors = [
  "#7ECA8F", "#FCA54D", "#B799DB", "#E56874",
  "#4097CC", "#FFD145", "#8C8C8C", "#F06292",
];
function getColor(index: number): string {
  return chartColors[index % chartColors.length] ?? "#8C8C8C";
}
function timeseriesKey(ts: Timeseries): string {
  return `${ts.measurement}-${ts.device}-${ts.location}-${ts.symbolicName}-${ts.field}`;
}
function channelLabel(ts: Timeseries): string {
  return [ts.device, ts.field, ts.location, ts.measurement, ts.symbolicName]
    .filter(Boolean)
    .join(" · ");
}

// Search text for the channel table.
const search = ref("");

// Channel table rows filtered by `search`.
const filteredTableItems = computed(() =>
  timeseriesDataTableItems.value.filter(item => channelMatchesSearch(item, search.value)),
);

const headers = [
  {
    title: "Select",
    key: "isSelected",
    sortable: true,
    sort: (a: boolean, b: boolean) => Number(b) - Number(a),
  },
  { title: "Measurement", key: "measurement", sortable: true },
  { title: "Device", key: "device", sortable: true },
  { title: "Location", key: "location", sortable: true },
  { title: "Symbolic Name", key: "symbolicName", sortable: true },
  { title: "Field", key: "field", sortable: true },
  // Inline summary metrics — no click required to see channel range/scale.
  { title: "Count", key: "metrics.COUNT", sortable: false, align: "end" as const },
  { title: "Min", key: "metrics.MIN", sortable: false, align: "end" as const },
  { title: "Max", key: "metrics.MAX", sortable: false, align: "end" as const },
  { title: "Mean", key: "metrics.MEAN", sortable: false, align: "end" as const },
  { title: "Annotations", key: "annotations", sortable: false },
];

function round(x: number): number {
  return Math.round(x * 1000) / 1000;
}

// V2CONV — compute COUNT/MIN/MAX/MEAN per channel client-side from the bulk
// points already in hand. Replaces the v1 per-row metrics N+1 fetch.
function applyMetrics(data: TimeseriesWithDataPoints[]) {
  const byKey = new Map<string, ChannelMetrics>();
  for (const series of data) {
    const values = series.points
      .map(pt => pt.value)
      .filter((v): v is number => typeof v === "number");
    if (values.length === 0) {
      byKey.set(timeseriesKey(series.timeseries), { COUNT: 0 });
      continue;
    }
    let min = Infinity;
    let max = -Infinity;
    let sum = 0;
    for (const v of values) {
      if (v < min) min = v;
      if (v > max) max = v;
      sum += v;
    }
    byKey.set(timeseriesKey(series.timeseries), {
      COUNT: values.length,
      MIN: round(min),
      MAX: round(max),
      MEAN: round(sum / values.length),
    });
  }
  for (const item of timeseriesDataTableItems.value) {
    item.metrics = byKey.get(timeseriesKey(item));
    item.metricsLoading = false;
    // No appId-keyed per-channel annotation source yet (REF-EDIT-TS-CHANNEL-ANN).
    item.annotations = [];
    item.annotationsLoading = false;
  }
}

// V2CONV — resolve shepardIds for the reference's channels by matching the
// payload 5-tuples against the container's v2 channels, then pull every
// channel's points in one bulk POST.
async function loadBulkChannelData() {
  const ref = timeseriesReference.value;
  const appId = containerAppId.value;
  if (!ref || !appId || ref.timeseries.length === 0) return;
  if (channelsV2.value.length === 0) return;
  const wantKeys = new Set(ref.timeseries.map(ts => timeseriesKey(ts)));
  const shepardIds = channelsV2.value
    .filter(ch =>
      wantKeys.has(
        timeseriesKey({
          measurement: ch.measurement ?? "",
          device: ch.device ?? "",
          location: ch.location ?? "",
          symbolicName: ch.symbolicName ?? "",
          field: ch.field ?? "",
        } as Timeseries),
      ),
    )
    .map(ch => ch.shepardId)
    .slice(0, 200);
  if (shepardIds.length === 0) {
    chartPayload.value = [];
    applyMetrics([]);
    return;
  }
  chartPayloadLoading.value = true;
  try {
    const data = await channelListingApi.value.getContainerBulkChannelData({
      appId,
      bulkChannelDataRequest: {
        shepardIds,
        // APISIMP-BULK-CHANNEL-REQ-NANOS-TO-ISO: ref.start/end are ns; API now takes ISO.
        start: new Date(Math.floor(ref.start / 1_000_000)).toISOString(),
        end: new Date(Math.floor(ref.end / 1_000_000)).toISOString(),
      },
    });
    chartPayload.value = (data ?? []) as TimeseriesWithDataPoints[];
    applyMetrics(chartPayload.value);
  } catch (e) {
    handleError(e as ResponseError, "loading timeseries channel data");
    chartPayload.value = [];
    applyMetrics([]);
  } finally {
    chartPayloadLoading.value = false;
  }
}

// Resolve the container's v2 channels (shepardId carriers) and name whenever
// the container appId becomes known.
watch(
  containerAppId,
  async (appId) => {
    if (!appId) return;
    try {
      const [channels, container] = await Promise.all([
        channelListingApi.value.listContainerChannels({
          appId,
          pageSize: MAX_CHANNEL_PAGE_SIZE,
        }),
        channelListingApi.value.getContainer({ appId }).catch(() => undefined),
      ]);
      channelsV2.value = channels ?? [];
      containerName.value = container?.name;
    } catch {
      // Best-effort; falls back to no auto-populate.
      channelsV2.value = [];
    }
  },
  { immediate: true },
);

// Build the table rows when the reference resolves.
watch(
  timeseriesReference,
  () => {
    if (!timeseriesReference.value) return;
    timeseriesDataTableItems.value = timeseriesReference.value.timeseries.map(
      timeseries => ({ ...timeseries, isSelected: false, metricsLoading: true }),
    );
  },
  { immediate: true },
);

// Kick off the bulk fetch when either the reference or the channel list lands.
watch([timeseriesReference, channelsV2], () => {
  void loadBulkChannelData();
});

const getSelectedTimeseries = () => {
  return timeseriesDataTableItems.value.filter(item => item.isSelected);
};

// V2CONV — build the CSV client-side from the bulk points already fetched
// (long format: channel,timestamp,value). Replaces the v1 export endpoint,
// which required the numeric ids that are @JsonIgnore-d on /v2.
function downloadTimeseries(filename: string) {
  const data = chartPayload.value;
  if (!data || data.length === 0) {
    handleError(
      new Error("No data points available to export"),
      "exporting timeseries reference",
    );
    return;
  }
  const lines = ["channel,timestamp,value"];
  for (const series of data) {
    const label = channelLabel(series.timeseries).replace(/"/g, '""');
    for (const pt of series.points) {
      lines.push(`"${label}",${pt.timestamp ?? ""},${pt.value ?? ""}`);
    }
  }
  const blob = new Blob([lines.join("\n")], { type: "text/csv;charset=utf-8;" });
  downloadFile(blob, filename + ".csv");
}

async function deleteTimeseriesReference() {
  const appId = timeseriesReference.value?.appId;
  if (!appId) return;
  try {
    await useV2ShepardApi(ReferencesApi).value.deleteReference({ appId });
    emitSuccess(
      `Successfully deleted timeseries reference "${timeseriesReference.value?.name}"`,
    );
    navigateTo(
      collectionsPath +
        routeParams.value.collectionId +
        dataObjectsPathFragment +
        routeParams.value.dataObjectId,
    );
  } catch (e) {
    handleError(e as ResponseError, "deleting timeseries reference");
  }
}

const onDelete = () => {
  showDeleteDialog.value = true;
};

// REF-EDIT-1: re-fetch the reference after an edit so the panel reflects the
// saved name/start/end. timeseriesReference is a computed off referenceV2, so
// we refresh the source rather than mutating the (read-only) view.
function onEditSaved() {
  refreshReferenceV2();
}

const onDownload = (name: string) => {
  downloadTimeseries(name);
};

const onSelectedItemChanged = () => {
  numberOfSelectedItems.value = getSelectedTimeseries().length;
};

const itemsPerPage = 10;

watch(timeseriesReference, () => {
  useHead({
    title: timeseriesReference.value?.name + " | shepard",
  });
});
</script>

<template>
  <div style="max-width: 1400px">
    <v-container fluid class="pa-0 fill-height" max-width="1000px">
      <v-row v-if="!!timeseriesReference && !!collection && !!dataObject">
        <v-col cols="12">
          <Breadcrumbs
            :items="[
              {
                title: 'Collections',
                to: collectionsPath,
              },
              {
                title: `${collection.name}`,
                to: collectionsPath + collection.id,
              },
              {
                title: dataObject.name,
                to:
                  collectionsPath +
                  routeParams.collectionId +
                  dataObjectsPathFragment +
                  routeParams.dataObjectId,
              },
              {
                title: timeseriesReference.name,
                to:
                  collectionsPath +
                  routeParams.collectionId +
                  dataObjectsPathFragment +
                  routeParams.dataObjectId +
                  timeseriesReferencePathFragment +
                  routeParams.timeseriesReferenceId,
              },
            ]"
          />
        </v-col>
        <v-col cols="12">
          <v-container fluid class="pa-0">
            <v-row no-gutters>
              <TitleAndMetadataDisplay
                :entity="{
                  ...timeseriesReference,
                  name: `Timeseries Reference '${timeseriesReference.name}'`,
                  type: 'Timeseries',
                  container: {
                    title:
                      containerName ??
                      timeseriesReference.referencedContainerName ??
                      'unknown name',
                    id: timeseriesReference.timeseriesContainerId ?? 0,
                    type: 'TIMESERIES',
                    availability:
                      timeseriesReference.referencedContainerAvailability,
                    appId: containerAppId,
                  },
                }"
                id-label="ID"
                :on-delete="onDelete"
                :on-download="onDownload"
                :on-edit="isAllowedToEditCollection ? () => (showEditDialog = true) : undefined"
              />
            </v-row>

            <!-- Channel Overview — all channels, locked to the reference's
                 time range. Matches the ExpansionPanelItem pattern used on
                 the TimeseriesContainer page. -->
            <ExpansionPanels class="mt-4 mb-2" :default-open="[0]">
              <ExpansionPanelItem title="Channel Overview">
                <template #append>
                  <v-btn
                    v-if="canVisualize3D"
                    size="small"
                    variant="tonal"
                    color="primary"
                    prepend-icon="mdi-cube-outline"
                    class="mr-2"
                    @click.stop="showVisualize3D = true"
                  >
                    Visualize in 3D
                  </v-btn>
                </template>
                <div
                  v-if="chartPayloadLoading || !chartPayloadFetched"
                  role="status"
                  class="d-flex align-center ga-2 text-medium-emphasis text-body-2 pa-4"
                >
                  <v-progress-circular indeterminate size="16" width="2" />
                  Loading…
                </div>
                <div
                  v-else-if="overviewSeries.length === 0"
                  class="text-medium-emphasis text-body-2 pa-2"
                >
                  No data points available.
                </div>
                <div style="overflow: hidden; min-width: 0;">
                  <TimeseriesChart
                    :series="overviewSeries"
                    :x-min="overviewXMin"
                    :x-max="overviewXMax"
                    :wall-clock-offset-ms="overviewWallClockOffsetMs"
                    show-legend
                    height="320px"
                  />
                </div>
              </ExpansionPanelItem>
            </ExpansionPanels>

            <!-- TM1a — time-reference model section -->
            <section class="page-section mt-4">
              <div class="page-section-head">
                <div class="text-h5 text-textbody1">Time reference</div>
              </div>
              <v-card variant="outlined" class="pa-3">
                <TimeReferencePanel
                  v-if="timeseriesReferenceAppId"
                  :app-id="timeseriesReferenceAppId"
                  :time-reference="timeseriesReference.timeReference"
                  :wall-clock-offset="timeseriesReference.wallClockOffset"
                  :wall-clock-offset-source="timeseriesReference.wallClockOffsetSource"
                  :can-edit="!!isAllowedToEditCollection"
                  @updated="onTimeReferenceUpdated"
                />
                <div
                  v-else
                  class="text-caption text-medium-emphasis"
                >
                  No appId — time-reference edit unavailable on this reference.
                </div>
              </v-card>
            </section>

            <v-row align="center" justify="space-between" class="mt-2">
              <v-col>
                <div class="text-caption text-medium-emphasis">
                  Interval:
                  {{
                    toShortDateTimeString(
                      parseDateFromNanos(timeseriesReference.start),
                    )
                  }}
                  –
                  {{
                    toShortDateTimeString(
                      parseDateFromNanos(timeseriesReference.end),
                    )
                  }}
                </div>
              </v-col>
              <!-- AI1c — quality score chip (per-reference, background-computed) -->
              <v-col cols="auto" class="d-flex align-center ga-2">
                <span class="text-caption text-medium-emphasis">Quality:</span>
                <QualityScoreChip :score="timeseriesReference.qualityScore" />
              </v-col>
              <v-col class="text-right" cols="auto">
                <div class="text-caption text-medium-emphasis">
                  Tick to compare — {{ numberOfSelectedItems }} / {{ MaxSelectableItems }} selected
                </div>
              </v-col>
            </v-row>
            <v-text-field
              v-model="search"
              clearable
              density="compact"
              hide-details
              placeholder="Search channels…"
              prepend-inner-icon="mdi-magnify"
              variant="outlined"
              class="mb-2 mt-1"
              style="max-width: 360px"
            />
            <div style="overflow-x: auto">
            <DataTable
              :items-per-page="itemsPerPage"
              :header-props="{
                class: 'text-subtitle-2 text-textbody1',
              }"
              :cell-props="{
                class: 'text-textbody1',
              }"
              :headers="headers"
              :items-for-pagination="filteredTableItems"
            >
              <template #[`item.isSelected`]="{ item }">
                <v-checkbox
                  v-model="item.isSelected"
                  density="compact"
                  hide-details
                  :disabled="
                    !item.isSelected &&
                    numberOfSelectedItems >= MaxSelectableItems
                  "
                  @update:model-value="() => onSelectedItemChanged()"
                />
              </template>
              <template #[`item.metrics.COUNT`]="{ item }">
                <span v-if="item.metrics?.COUNT" class="text-mono">{{ item.metrics.COUNT }}</span>
                <v-progress-circular v-else-if="item.metricsLoading" indeterminate size="12" width="2" aria-label="Loading metric" />
                <span v-else class="text-medium-emphasis">—</span>
              </template>
              <template #[`item.metrics.MIN`]="{ item }">
                <span v-if="item.metrics?.MIN" class="text-mono">{{ item.metrics.MIN }}</span>
                <v-progress-circular v-else-if="item.metricsLoading" indeterminate size="12" width="2" aria-label="Loading metric" />
                <span v-else class="text-medium-emphasis">—</span>
              </template>
              <template #[`item.metrics.MAX`]="{ item }">
                <span v-if="item.metrics?.MAX" class="text-mono">{{ item.metrics.MAX }}</span>
                <v-progress-circular v-else-if="item.metricsLoading" indeterminate size="12" width="2" aria-label="Loading metric" />
                <span v-else class="text-medium-emphasis">—</span>
              </template>
              <template #[`item.metrics.MEAN`]="{ item }">
                <span v-if="item.metrics?.MEAN" class="text-mono">{{ item.metrics.MEAN }}</span>
                <v-progress-circular v-else-if="item.metricsLoading" indeterminate size="12" width="2" aria-label="Loading metric" />
                <span v-else class="text-medium-emphasis">—</span>
              </template>
              <template #[`item.annotations`]="{ item }">
                <v-progress-circular
                  v-if="item.annotationsLoading"
                  indeterminate
                  aria-label="Loading annotations"
                  size="12"
                  width="2"
                />
                <div
                  v-else-if="item.annotations && item.annotations.length > 0"
                  class="d-flex flex-wrap ga-1"
                >
                  <SemanticAnnotationChip
                    v-for="ann in item.annotations"
                    :key="ann.id"
                    :annotation="ann"
                    :can-delete="false"
                    :annotated-type="
                      new AnnotatedReference(timeseriesReferenceAppId ?? '', 'TimeseriesReference')
                    "
                  />
                </div>
                <span v-else class="text-medium-emphasis">—</span>
              </template>
              <template #bottom>
                <v-divider :thickness="8" color="divider2" opacity="1" />
                <v-pagination :total-visible="6" />
              </template>
            </DataTable>
            </div>

            <!-- Reference-level semantic annotations (tags / labels for this
                 reference as a whole, distinct from per-channel annotations
                 in the table and anomaly intervals below). -->
            <section class="page-section mt-6">
              <div class="page-section-head">
                <div class="text-h5 text-textbody1">Semantic Annotations</div>
                <AddAnnotationButton
                  v-if="isAllowedToEditCollection && timeseriesReferenceAppId"
                  :annotated="new AnnotatedReference(timeseriesReferenceAppId, 'TimeseriesReference')"
                />
              </div>
              <SemanticAnnotationList
                v-if="timeseriesReferenceAppId"
                :annotated="new AnnotatedReference(timeseriesReferenceAppId, 'TimeseriesReference')"
                :can-delete="!!isAllowedToEditCollection"
              />
            </section>

            <!-- TA1a / AI1b — interval & point annotations on this reference.
                 Shown inline so users see anomalies without opening a dialog. -->
            <section class="page-section mt-6">
              <div class="page-section-head d-flex align-center ga-2">
                <div class="text-h5 text-textbody1">Anomalies &amp; intervals</div>
                <v-chip
                  v-if="tsAnnotations.length > 0"
                  size="small"
                  variant="tonal"
                >
                  {{ tsAnnotations.length }}
                </v-chip>
                <v-spacer />
                <v-btn
                  v-if="isAllowedToEditCollection && timeseriesReferenceAppId"
                  variant="tonal"
                  size="small"
                  color="primary"
                  prepend-icon="mdi-magnify-scan"
                  @click="showDetectAnomalies = true"
                >
                  Detect anomalies
                </v-btn>
              </div>

              <div
                v-if="tsAnnotationsLoading"
                role="status"
                class="d-flex align-center ga-2 text-medium-emphasis text-body-2 pa-2"
              >
                <v-progress-circular indeterminate size="14" width="2" />
                Loading annotations…
              </div>
              <div
                v-else-if="tsAnnotations.length === 0"
                class="text-medium-emphasis text-body-2 pa-2"
              >
                No anomalies or labelled intervals on this reference yet.
                {{ isAllowedToEditCollection ? "Click \"Detect anomalies\" to scan." : "" }}
              </div>
              <div v-else class="d-flex flex-column ga-2">
                <div
                  v-for="ann in tsAnnotations"
                  :key="ann.appId"
                  class="annotation-card pa-3"
                >
                  <div class="d-flex align-center ga-2 mb-1">
                    <v-chip
                      v-if="ann.aiGenerated"
                      size="x-small"
                      variant="tonal"
                      color="primary"
                      prepend-icon="mdi-robot-outline"
                    >AI</v-chip>
                    <div class="text-body-2 font-weight-medium">{{ ann.label }}</div>
                    <v-spacer />
                    <span
                      v-if="ann.confidence != null"
                      class="text-caption text-medium-emphasis"
                    >
                      confidence {{ Math.round(ann.confidence * 100) }}%
                    </span>
                    <v-btn
                      v-if="isAllowedToEditCollection"
                      density="comfortable"
                      variant="text"
                      size="x-small"
                      icon="mdi-close"
                      @click="deleteTsAnnotation(ann.appId)"
                    />
                  </div>
                  <div class="text-caption text-medium-emphasis text-mono">
                    {{ formatTsAnnotationRange(ann) }}
                  </div>
                  <div v-if="ann.description" class="text-body-2 mt-1">
                    {{ ann.description }}
                  </div>
                </div>
              </div>
            </section>
          </v-container>
        </v-col>
      </v-row>
      <!-- UX612-C1: 404 on the v2 reference load → honest empty state
           (UU1 pattern) instead of the former eternal spinner. -->
      <EntityNotFound
        v-else-if="referenceNotFound"
        entity-kind="TimeseriesReference"
        :requested-id="routeParams.timeseriesReferenceId ?? ''"
        :parent-route="
          collectionsPath +
          routeParams.collectionId +
          dataObjectsPathFragment +
          routeParams.dataObjectId
        "
      />
      <CenteredLoadingSpinner v-else />
    </v-container>
    <ViewRecipeBuilderDialog
      v-if="timeseriesReference && timeseriesReference.timeseriesContainerId"
      v-model="showVisualize3D"
      :container-id="timeseriesReference.timeseriesContainerId"
      :container-app-id="containerAppId ?? ''"
      :channels="timeseriesReference.timeseries"
      :channels-v2="channelsV2.length ? channelsV2 : undefined"
      :start-ns="timeseriesReference.start"
      :end-ns="timeseriesReference.end"
      :data-object-app-id="dataObjectIdStr || undefined"
    />
    <ConfirmDeleteDialog
      v-model:show-dialog="showDeleteDialog"
      @confirmed="deleteTimeseriesReference"
    />
    <DetectAnomaliesDialog
      v-if="timeseriesReferenceAppId"
      v-model:show-dialog="showDetectAnomalies"
      :ref-app-id="timeseriesReferenceAppId"
      @annotations-saved="refetchTsAnnotations"
    />
    <EditTimeseriesReferenceDialog
      v-if="timeseriesReferenceAppId && timeseriesReference"
      v-model:show-dialog="showEditDialog"
      :timeseries-reference-app-id="timeseriesReferenceAppId"
      :current-name="timeseriesReference.name"
      :current-start="timeseriesReference.start"
      :current-end="timeseriesReference.end"
      :current-channels="timeseriesReference.timeseries"
      :available-channels="channelsV2.map(ch => ({ measurement: ch.measurement ?? '', device: ch.device ?? '', location: ch.location ?? '', symbolicName: ch.symbolicName ?? '', field: ch.field ?? '' }))"
      @saved="onEditSaved"
    />
  </div>
</template>

<style scoped lang="scss">
.text-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-variant-numeric: tabular-nums;
  font-size: 0.85em;
}
.page-section {
  margin-bottom: 24px;
}
.page-section-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.annotation-card {
  background: rgba(var(--v-border-color), 0.05);
  border-left: 3px solid rgb(var(--v-theme-primary));
  border-radius: 4px;
}

.v-table {
  :deep(.word-wrap-anywhere) {
    word-wrap: anywhere;
  }
}
</style>
