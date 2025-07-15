<script setup lang="ts">
import type { ResponseError, Timeseries } from "@dlr-shepard/backend-client";
import { TimeseriesReferenceApi } from "@dlr-shepard/backend-client";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import { useFetchTimeseriesReference } from "~/composables/context/useFetchTimeseriesReferences";

definePageMeta({ layout: "collection" });

useHead({
  title: "Timeseries Reference  | shepard",
});

interface TimeseriesDataTableItem extends Timeseries {
  isSelected: boolean;
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
];

watch(timeseriesReference, () => {
  if (timeseriesReference.value) {
    timeseriesDataTableItems.value = timeseriesReference.value?.timeseries.map(
      timeseries => {
        return { ...timeseries, isSelected: false };
      },
    );
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
              <template #bottom>
                <v-divider :thickness="8" color="divider2" opacity="1" />
                <v-pagination :total-visible="6" />
              </template>
            </DataTable>
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
.v-table {
  :deep(.word-wrap-anywhere) {
    word-wrap: anywhere;
  }

  :deep(tbody) > tr > td {
    padding: 20px 24px !important;
  }
}
</style>
