<script setup lang="ts">
import type {
  ResponseError,
  Timeseries,
  TimeseriesContainer,
} from "@dlr-shepard/backend-client";
import { TimeseriesReferenceApi } from "@dlr-shepard/backend-client";
import { useFetchTimeSeriesContainer } from "~/composables/context/useFetchTimeseriesContainer";
import { useFetchTimeseriesReference } from "~/composables/context/useFetchTimeseriesReferences";

definePageMeta({ layout: "collection" });

useHead({
  title: "Timeseries Reference  | shepard",
});

interface TimeseriesDataTableItem extends Timeseries {
  isSelected: boolean;
}

const maxSelectableItems = 7;

const { routeParams } = useCollectionRouteParams();
const { collectionId, dataObjectId, timeseriesReferenceId } =
  routeParams.value as CollectionRouteParams & {
    dataObjectId: number;
    timeseriesReferenceId: number;
  };

const { collection } = useFetchCollection(collectionId);
const { dataObject } = useFetchDataObject(collectionId, dataObjectId);

const { timeseriesReference } = useFetchTimeseriesReference(
  collectionId,
  dataObjectId,
  timeseriesReferenceId,
);

const timeseriesContainer = ref<TimeseriesContainer | undefined>(undefined);
const timeseriesDataTableItems = ref<TimeseriesDataTableItem[]>([]);
const numberOfSelectedItems = ref<number>(0);
const showDeleteDialog = ref<boolean>(false);
const showTimeseriesReferenceDialog = ref<boolean>(false);
const headers = ref([
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
]);

watch(timeseriesReference, () => {
  if (timeseriesReference.value) {
    timeseriesDataTableItems.value = timeseriesReference.value?.timeseries.map(
      timeseries => {
        return { ...timeseries, isSelected: false };
      },
    );
  }
});

// Could't find better solution other than watch
watch(
  () => timeseriesReference.value,
  async newReference => {
    if (newReference) {
      const data = useFetchTimeSeriesContainer(
        newReference.timeseriesContainerId,
      );

      watch(
        () => data.timeseriesContainer.value,
        async newReference => {
          timeseriesContainer.value = newReference;
        },
      );
    }
  },
);

const getSelectedTimeseries = () => {
  return timeseriesDataTableItems.value.filter(item => item.isSelected);
};

const plotSelectedTimeseries = () => {
  showTimeseriesReferenceDialog.value = true;
};

const downloadTimeseries = (filename: string) => {
  createApiInstance(TimeseriesReferenceApi)
    .exportTimeseriesPayload({
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
  await createApiInstance(TimeseriesReferenceApi)
    .deleteTimeseriesReference({
      collectionId,
      dataObjectId,
      timeseriesReferenceId,
    })
    .then(() => {
      emitSuccess(
        `Successfully deleted timeseries reference "${timeseriesReference.value?.name}"`,
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
</script>

<template>
  <div style="max-width: 1000px">
    <v-container fluid class="pa-0 fill-height" max-width="1000px">
      <v-row
        v-if="
          !!timeseriesReference &&
          !!collection &&
          !!dataObject &&
          !!timeseriesContainer
        "
      >
        <v-col cols="12">
          <Breadcrumbs
            :items="[
              {
                title: 'Collections',
                to: collectionsPath,
              },
              {
                title: `Collection '${collection.name}'`,
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
                    title: timeseriesContainer.name,
                    id: timeseriesContainer.id,
                    path: containersPath + timeseriesContainer.id,
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
                    toDateTimeStringWithMilliSeconds(
                      parseDateFromNanos(timeseriesReference.start),
                    )
                  }}
                  -
                  {{
                    toDateTimeStringWithMilliSeconds(
                      parseDateFromNanos(timeseriesReference.end),
                    )
                  }}
                </div>
              </v-col>
              <v-col class="text-right" cols="auto">
                <div class="pa-4">
                  Selected items: {{ numberOfSelectedItems }} /
                  {{ maxSelectableItems }}
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
                    numberOfSelectedItems > maxSelectableItems
                  "
                  @click="plotSelectedTimeseries"
                >
                  Metrics and Plotter
                </v-btn>
              </v-col>
            </v-row>
            <DataTable
              :header-props="{
                class: 'text-subtitle-2 text-textbody1',
              }"
              :cell-props="{
                class: 'text-textbody1',
              }"
              :headers="headers"
              :items="timeseriesDataTableItems"
              item-value="measurement"
            >
              <template #[`item.isSelected`]="{ item }">
                <v-checkbox
                  v-model="item.isSelected"
                  density="compact"
                  hide-details
                  :disabled="
                    !item.isSelected &&
                    numberOfSelectedItems >= maxSelectableItems
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
      v-if="showTimeseriesReferenceDialog"
      v-model:show-dialog="showTimeseriesReferenceDialog"
      :collection-id="collectionId"
      :data-object-id="dataObjectId"
      :timeseries-reference-id="timeseriesReferenceId"
      :timeseries="getSelectedTimeseries()"
      :timeseries-container-id="timeseriesContainer?.id ?? -1"
      :timeseries-reference="timeseriesReference"
    />
  </div>
</template>

<style scoped lang="scss">
.v-table {
  :deep(thead) > tr > th {
    background-color: rgb(var(--v-theme-divider2));
  }

  :deep(.word-wrap-anywhere) {
    word-wrap: anywhere;
  }

  :deep(tbody) > tr > td {
    padding: 20px 24px !important;
  }
}
</style>
