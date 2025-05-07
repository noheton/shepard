<script setup lang="ts">
import {
  ContainerType,
  FileContainerApi,
  FileReferenceApi,
  StructuredDataContainerApi,
  StructuredDataReferenceApi,
  TimeseriesContainerApi,
  TimeseriesReferenceApi,
  type ResponseError,
  type TimeseriesEntity,
} from "@dlr-shepard/backend-client";
import { toShortDateString } from "~/utils/helpers";
import type { FileRef, TimeseriesRef } from "./DataRef";
const props = defineProps<{ collectionId: number; dataObjectId: number }>();
const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});
const emit = defineEmits(["data-reference-created"]);

const router = useRouter();

const dataReferenceName = ref<string>("");
const dataReferenceContainerId = ref<number | undefined>(undefined);
const fileRef = ref<FileRef | undefined>(undefined);
const timeseriesRef = ref<TimeseriesRef | undefined>(undefined);

const loading = ref<boolean>(false);

const isValid = ref<boolean>(true);

const chosenContainerType = ref<ContainerType | null>(null);

async function createDataReference() {
  if (!dataReferenceContainerId.value) return;
  if (chosenContainerType.value === ContainerType.File && fileRef.value) {
    createApiInstance(FileReferenceApi)
      .createFileReference({
        collectionId: props.collectionId,
        dataObjectId: props.dataObjectId,
        fileReference: {
          ...fileRef.value,
          name: dataReferenceName.value,
          fileContainerId: dataReferenceContainerId.value,
        },
      })
      .then(response => {
        emitSuccess(`Successfully created data reference "${response.name}"`);
        emit("data-reference-created");
        router.push(
          collectionsPath +
            props.collectionId +
            dataObjectsPathFragment +
            props.dataObjectId +
            fileReferencesPathFragment +
            response.id,
        );
        showDialog.value = false;
      })
      .catch(error => {
        handleError(error, "createDataReference");
      });
  } else if (
    chosenContainerType.value === ContainerType.Structureddata &&
    fileRef.value
  ) {
    createApiInstance(StructuredDataReferenceApi)
      .createStructuredDataReference({
        collectionId: props.collectionId,
        dataObjectId: props.dataObjectId,
        structuredDataReference: {
          structuredDataOids: fileRef.value.fileOids,
          name: dataReferenceName.value,
          structuredDataContainerId: dataReferenceContainerId.value,
        },
      })
      .then(response => {
        emitSuccess(`Successfully created data reference "${response.name}"`);
        emit("data-reference-created");
        router.push(
          collectionsPath +
            props.collectionId +
            dataObjectsPathFragment +
            props.dataObjectId +
            structuredDataReferencesPathFragment +
            response.id,
        );
        showDialog.value = false;
      })
      .catch(error => {
        handleError(error, "createDataReference");
      });
  } else if (
    chosenContainerType.value === ContainerType.Timeseries &&
    timeseriesRef.value
  ) {
    createApiInstance(TimeseriesReferenceApi)
      .createTimeseriesReference({
        collectionId: props.collectionId,
        dataObjectId: props.dataObjectId,
        timeseriesReference: {
          ...timeseriesRef.value,
          name: dataReferenceName.value,
          timeseriesContainerId: dataReferenceContainerId.value,
        },
      })
      .then(response => {
        emitSuccess(`Successfully created data reference "${response.name}"`);
        emit("data-reference-created");
        router.push(
          collectionsPath +
            props.collectionId +
            dataObjectsPathFragment +
            props.dataObjectId +
            timeseriesReferencePathFragment +
            response.id,
        );
        showDialog.value = false;
      })
      .catch(error => {
        handleError(error, "createDataReference");
      });
  }
}

const fileContainerApi = createApiInstance(FileContainerApi);
const timeseriesContainerApi = createApiInstance(TimeseriesContainerApi);
const structuredDataContainerApi = createApiInstance(
  StructuredDataContainerApi,
);

function getContainerById(containerId: number, containerType: ContainerType) {
  chosenContainerType.value = containerType;
  if (containerType === ContainerType.File) {
    getAllFiles(containerId);
  }
  if (containerType === ContainerType.Timeseries) {
    getAllTimeseries(containerId);
  }
  if (containerType === ContainerType.Structureddata) {
    getAllStructuredDatas(containerId);
  }
  if (containerType === ContainerType.Spatialdata) {
    console.log("not implemented yet " + containerType);
  }
}

export interface fileItem {
  oid?: string;
  filename: string;
  createdAt: string;
}

export type TimeseriesRefItem = Omit<TimeseriesEntity, "containerId">;

const fileList = ref<fileItem[] | undefined>(undefined);
const timeseriesList = ref<TimeseriesRefItem[] | undefined>(undefined);

function getAllFiles(containerId: number) {
  loading.value = true;
  fileContainerApi
    .getAllFiles({
      fileContainerId: containerId,
    })
    .then(response => {
      fileList.value = response.map(file => ({
        oid: file.oid,
        filename: file.filename ?? "",
        createdAt: toShortDateString(file.createdAt ?? null) ?? "",
      }));
    })
    .catch(e => {
      handleError(e as ResponseError, "getting all Files");
    })
    .finally(() => (loading.value = false));
}

function getAllTimeseries(containerId: number) {
  loading.value = true;
  timeseriesContainerApi
    .getTimeseriesOfContainer({
      timeseriesContainerId: containerId,
    })
    .then(response => {
      timeseriesList.value = response;
    })
    .catch(e => {
      handleError(e as ResponseError, "getting all Files");
    })
    .finally(() => (loading.value = false));
}

function getAllStructuredDatas(containerId: number) {
  structuredDataContainerApi
    .getAllStructuredDatas({
      structuredDataContainerId: containerId,
    })
    .then(response => {
      fileList.value = response.map(d => ({
        oid: d.oid,
        filename: d.name ?? "",
        createdAt: toShortDateString(d.createdAt ?? null) ?? "",
      }));
    })
    .catch(e => {
      handleError(e as ResponseError, "getting all structured datas");
    });
}
</script>

<template>
  <FormDialog
    v-model:show-dialog="showDialog"
    :max-width="800"
    title="Create Data Reference"
    :submit-disabled="
      !isValid ||
      !dataReferenceContainerId ||
      (!(fileRef && fileRef?.fileOids.length > 0) &&
        !(timeseriesRef && timeseriesRef?.timeseries.length > 0))
    "
    save-button-text="Add"
    @submit="createDataReference"
  >
    <template #form>
      <v-form ref="form" v-model="isValid">
        <v-row class="pt-9 pb-1">
          <v-col>
            <NameInput v-model:name="dataReferenceName" />
          </v-col>
        </v-row>
        <v-row>
          <v-col>
            <ContainerInput
              v-model:container-id="dataReferenceContainerId"
              :collection-id="collectionId"
              @container-selected="getContainerById"
              @selection-cleared="dataReferenceContainerId = undefined"
            />
          </v-col>
        </v-row>

        <v-row v-if="dataReferenceContainerId">
          <v-col class="pb-5">
            <div class="text-subtitle-1">Select Data</div>
          </v-col>
        </v-row>
        <v-row v-if="dataReferenceContainerId">
          <v-col class="pt-1">
            <FileReferencePicker
              v-if="
                chosenContainerType === ContainerType.File ||
                chosenContainerType === ContainerType.Structureddata
              "
              v-model:file-reference="fileRef"
              :items="fileList"
              :loading="loading"
            />
            <TimeseriesReferencePicker
              v-if="chosenContainerType === ContainerType.Timeseries"
              v-model:timeseries-reference="timeseriesRef"
              :items="timeseriesList"
              :loading="loading"
            />
          </v-col>
        </v-row>
        <v-row>
          <v-col class="pt-1">
            <MandatoryFieldHint />
          </v-col>
        </v-row>
      </v-form>
    </template>
  </FormDialog>
</template>
