<script setup lang="ts">
import type { ResponseError, Timeseries } from "@dlr-shepard/backend-client";
import { TimeseriesReferenceApi } from "@dlr-shepard/backend-client";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import {
  useFetchTimeseriesReferenceMetrics,
  type Metrics,
} from "~/composables/context/useFetchTimeseriesReferencesMetrics";
import { useFetchTimeseriesReference } from "~/composables/context/useFetchTimeseriesReferences";
import { useTimeseriesReferenceAnnotations } from "~/composables/context/useTimeseriesReferenceAnnotations";

definePageMeta({ layout: "collection" });

interface TimeseriesDataTableItem extends Timeseries {
  isSelected: boolean;
  // Per-channel summary metrics, fetched lazily after the reference loads.
  // Surfacing these inline (instead of hiding them behind the Graph & Metrics
  // dialog) means users see range / mean / count at a scan without clicking.
  metrics?: Partial<Metrics>;
  metricsLoading?: boolean;
}

const MaxSelectableItems = 7;

const { routeParams } = useCollectionRouteParams();
const { collectionId, dataObjectId, timeseriesReferenceId } =
  routeParams.value as CollectionRouteParams & {
    dataObjectId: number;
    timeseriesReferenceId: number;
  };

const { collection, isAllowedToEditCollection } =
  useFetchCollection(collectionId);
const { dataObject } = useFetchDataObject(collectionId, dataObjectId);

const { timeseriesReference } = useFetchTimeseriesReference(
  collectionId,
  dataObjectId,
  timeseriesReferenceId,
);

// TA1a + AI1b — show interval/point annotations on this reference and let
// editors invoke anomaly detection inline. The generated client doesn't yet
// expose appId, so cast defensively (every Java entity carries appId since L2a).
const timeseriesReferenceAppId = computed<string | undefined>(() => {
  const raw = timeseriesReference.value as unknown as { appId?: string } | undefined;
  return raw?.appId ?? undefined;
});
const {
  annotations: tsAnnotations,
  loading: tsAnnotationsLoading,
  detecting: anomalyDetecting,
  deleteAnnotation: deleteTsAnnotation,
  detectAnomalies,
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
const showDeleteDialog = ref<boolean>(false);
const showTimeseriesReferenceDialog = ref<boolean>(false);
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
];

async function fetchMetricsForRow(item: TimeseriesDataTableItem) {
  item.metricsLoading = true;
  try {
    const m = await useFetchTimeseriesReferenceMetrics(
      collectionId,
      dataObjectId,
      timeseriesReferenceId,
      item.measurement,
      item.device,
      item.location,
      item.symbolicName,
      item.field,
    );
    if (m) item.metrics = m;
  } finally {
    item.metricsLoading = false;
  }
}

watch(timeseriesReference, () => {
  if (timeseriesReference.value) {
    timeseriesDataTableItems.value = timeseriesReference.value?.timeseries.map(
      timeseries => {
        return { ...timeseries, isSelected: false };
      },
    );
    // Fan out metric fetches in parallel — the backend caps concurrency on
    // its side, and per-row latency is independent.
    timeseriesDataTableItems.value.forEach(item => {
      void fetchMetricsForRow(item);
    });
  }
});

const getSelectedTimeseries = () => {
  return timeseriesDataTableItems.value.filter(item => item.isSelected);
};

const plotSelectedTimeseries = () => {
  showTimeseriesReferenceDialog.value = true;
};

const downloadTimeseries = (filename: string) => {
  useShepardApi(TimeseriesReferenceApi)
    .value.exportTimeseriesPayload({
      collectionId,
      dataObjectId,
      timeseriesReferenceId,
    })
    .then(response => {
      downloadFile(response, filename + ".csv");
    })
    .catch(e => {
      handleError(e as ResponseError, "exporting timeseries reference");
    });
};

async function deleteTimeseriesReference() {
  await useShepardApi(TimeseriesReferenceApi)
    .value.deleteTimeseriesReference({
      collectionId,
      dataObjectId,
      timeseriesReferenceId,
    })
    .then(() => {
      emitSuccess(
        `Successfully deleted timeseries reference "${timeseriesReference.value?.name}"`,
      );
      navigateTo(
        collectionsPath + collectionId + dataObjectsPathFragment + dataObjectId,
      );
    })
    .catch(e => {
      handleError(e as ResponseError, "deleting timeseries reference");
    });
}

const onDelete = () => {
  showDeleteDialog.value = true;
};

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
  <div style="max-width: 1000px">
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
                  collectionId +
                  dataObjectsPathFragment +
                  dataObjectId,
              },
              {
                title: timeseriesReference.name,
                to:
                  collectionsPath +
                  collectionId +
                  dataObjectsPathFragment +
                  dataObjectId +
                  timeseriesReferencePathFragment +
                  timeseriesReference.id,
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
                  name: `Timeseries Reference “${timeseriesReference.name}”`,
                  type: 'Timeseries',
                  container: {
                    title:
                      timeseriesReference.referencedContainerName ??
                      'unknown name',
                    id: timeseriesReference.timeseriesContainerId,
                    type: 'TIMESERIES',
                    availability:
                      timeseriesReference.referencedContainerAvailability,
                  },
                }"
                id-label="ID"
                :on-delete="onDelete"
                :on-download="onDownload"
              />
            </v-row>
            <v-row align="center" justify="space-between">
              <v-col>
                <div class="pa-4">
                  Interval:
                  {{
                    toShortDateTimeString(
                      parseDateFromNanos(timeseriesReference.start),
                    )
                  }}
                  -
                  {{
                    toShortDateTimeString(
                      parseDateFromNanos(timeseriesReference.end),
                    )
                  }}
                </div>
              </v-col>
              <v-col class="text-right" cols="auto">
                <div class="pa-4">
                  Selected items: {{ numberOfSelectedItems }} /
                  {{ MaxSelectableItems }}
                </div>
              </v-col>
              <v-col class="text-right" cols="auto">
                <v-btn
                  rounded="lg"
                  variant="flat"
                  color="primary"
                  prepend-icon="mdi-chart-line"
                  :disabled="
                    numberOfSelectedItems === 0 ||
                    numberOfSelectedItems > MaxSelectableItems
                  "
                  @click="plotSelectedTimeseries"
                >
                  Metrics and Plotter
                </v-btn>
              </v-col>
            </v-row>
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
              :items-for-pagination="timeseriesDataTableItems"
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
                <v-progress-circular v-else-if="item.metricsLoading" indeterminate size="12" width="2" />
                <span v-else class="text-medium-emphasis">—</span>
              </template>
              <template #[`item.metrics.MIN`]="{ item }">
                <span v-if="item.metrics?.MIN" class="text-mono">{{ item.metrics.MIN }}</span>
                <v-progress-circular v-else-if="item.metricsLoading" indeterminate size="12" width="2" />
                <span v-else class="text-medium-emphasis">—</span>
              </template>
              <template #[`item.metrics.MAX`]="{ item }">
                <span v-if="item.metrics?.MAX" class="text-mono">{{ item.metrics.MAX }}</span>
                <v-progress-circular v-else-if="item.metricsLoading" indeterminate size="12" width="2" />
                <span v-else class="text-medium-emphasis">—</span>
              </template>
              <template #[`item.metrics.MEAN`]="{ item }">
                <span v-if="item.metrics?.MEAN" class="text-mono">{{ item.metrics.MEAN }}</span>
                <v-progress-circular v-else-if="item.metricsLoading" indeterminate size="12" width="2" />
                <span v-else class="text-medium-emphasis">—</span>
              </template>
              <template #bottom>
                <v-divider :thickness="8" color="divider2" opacity="1" />
                <v-pagination :total-visible="6" />
              </template>
            </DataTable>
            </div>

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
                  :loading="anomalyDetecting"
                  @click="detectAnomalies"
                >
                  Run anomaly detection
                </v-btn>
              </div>

              <div
                v-if="tsAnnotationsLoading"
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
                {{ isAllowedToEditCollection ? "Click \"Run anomaly detection\" to scan." : "" }}
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
      <CenteredLoadingSpinner v-else />
    </v-container>
    <ConfirmDeleteDialog
      v-model:show-dialog="showDeleteDialog"
      @confirmed="deleteTimeseriesReference"
    />
    <ShowTimeseriesReferenceDialog
      v-if="
        showTimeseriesReferenceDialog &&
        timeseriesReference?.timeseriesContainerId
      "
      v-model:show-dialog="showTimeseriesReferenceDialog"
      :collection-id="collectionId"
      :data-object-id="dataObjectId"
      :timeseries-reference-id="timeseriesReferenceId"
      :timeseries="getSelectedTimeseries()"
      :timeseries-container-id="timeseriesReference?.timeseriesContainerId"
      :timeseries-reference="timeseriesReference"
      :is-allowed-to-edit-collection="isAllowedToEditCollection"
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

  :deep(tbody) > tr > td {
    padding: 20px 24px !important;
  }
}
</style>
