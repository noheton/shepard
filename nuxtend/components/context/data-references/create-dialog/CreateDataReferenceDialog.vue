<script setup lang="ts">
import {
  FileContainerApi,
  FileReferenceApi,
  SpatialDataContainerApi,
  StructuredDataContainerApi,
  TimeseriesContainerApi,
  type ContainerType,
  type ResponseError,
  StructuredDataReferenceApi,
} from "@dlr-shepard/backend-client";
import { toShortDateString } from "~/utils/helpers";

const props = defineProps<{ collectionId: number; dataObjectId: number }>();
const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});
const emit = defineEmits(["data-reference-created"]);

const router = useRouter();

const dataReferenceName = ref<string>("");
const dataReferenceContainerId = ref<number | null>(null);
const dataOids = ref<Array<string>>([""]);
const loading = ref<boolean>(false);

const isValid = ref<boolean>(true);
const containerChosen = ref<boolean>(false);

const chosenContainerType = ref<ContainerType | null>(null);

watch(dataReferenceContainerId, () => {
  if (dataReferenceContainerId.value) containerChosen.value = true;
  if (!dataReferenceContainerId.value) containerChosen.value = false;
});

async function createDataReference() {
  if (!dataReferenceContainerId.value) return;
  if (chosenContainerType.value == "FILE") {
    createApiInstance(FileReferenceApi)
      .createFileReference({
        collectionId: props.collectionId,
        dataObjectId: props.dataObjectId,
        fileReference: {
          name: dataReferenceName.value,
          fileContainerId: dataReferenceContainerId.value,
          fileOids: dataOids.value,
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
  } else if (chosenContainerType.value == "STRUCTUREDDATA") {
    createApiInstance(StructuredDataReferenceApi)
      .createStructuredDataReference({
        collectionId: props.collectionId,
        dataObjectId: props.dataObjectId,
        structuredDataReference: {
          name: dataReferenceName.value,
          structuredDataContainerId: dataReferenceContainerId.value,
          structuredDataOids: dataOids.value,
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
  }
}

const fileContainerApi = createApiInstance(FileContainerApi);
const timeseriesContainerApi = createApiInstance(TimeseriesContainerApi);
const structuredDataContainerApi = createApiInstance(
  StructuredDataContainerApi,
);
const spatialDataContainerApi = createApiInstance(SpatialDataContainerApi);

function getContainerById(containerId: number, containerType: ContainerType) {
  if (containerType == "FILE") {
    fileContainerApi
      .getFileContainer({
        fileContainerId: containerId,
      })
      .then(response => {
        getAllFiles(response.id);
      })
      .catch(e => {
        handleError(e as ResponseError, "getting file container");
      });
  }
  if (containerType == "TIMESERIES") {
    timeseriesContainerApi
      .getTimeseriesContainer({
        timeseriesContainerId: containerId,
      })
      .then(response => {
        console.log("not implemented yet" + response.type);
      })
      .catch(e => {
        handleError(e as ResponseError, "get timeseries container");
      });
  }
  if (containerType == "STRUCTUREDDATA") {
    structuredDataContainerApi
      .getStructuredDataContainer({
        structuredDataContainerId: containerId,
      })
      .then(response => {
        getAllStructuredDatas(response.id);
      })
      .catch(e => {
        handleError(e as ResponseError, "getting structured data container");
      });
  }
  if (containerType == "SPATIALDATA") {
    spatialDataContainerApi
      .getSpatialDataContainer({
        spatialDataContainerId: containerId,
      })
      .then(response => {
        console.log("not implemented yet" + response.type);
      })
      .catch(e => {
        handleError(e as ResponseError, "getting spatial data container");
      });
  }
}

export interface DisplayItem {
  oid?: string;
  filename: string;
  createdAt: string;
}

class DisplayItemImpl implements DisplayItem {
  oid?: string;
  filename: string;
  createdAt: string;

  constructor(oid: string | undefined, filename: string, createdAt: string) {
    this.oid = oid;
    this.filename = filename;
    this.createdAt = createdAt;
  }
}

const displayItems = ref<DisplayItem[]>();
function getAllFiles(containerId: number) {
  fileContainerApi
    .getAllFiles({
      fileContainerId: containerId,
    })
    .then(response => {
      chosenContainerType.value = "FILE";
      displayItems.value = response.map(d => {
        return new DisplayItemImpl(
          d.oid,
          d.filename ?? "",
          toShortDateString(d.createdAt ?? null) ?? "",
        );
      });
    })
    .catch(e => {
      handleError(e as ResponseError, "getting all Files");
    });
}

function getAllStructuredDatas(containerId: number) {
  structuredDataContainerApi
    .getAllStructuredDatas({
      structuredDataContainerId: containerId,
    })
    .then(response => {
      chosenContainerType.value = "STRUCTUREDDATA";
      displayItems.value = response.map(d => {
        return new DisplayItemImpl(
          d.oid,
          d.name ?? "",
          toShortDateString(d.createdAt ?? null) ?? "",
        );
      });
    })
    .catch(e => {
      handleError(e as ResponseError, "getting all structured datas");
    });
}

function getDataOidList(oid: string[]) {
  dataOids.value = oid;
}
</script>

<template>
  <FormDialog
    v-model:show-dialog="showDialog"
    :max-width="800"
    title="Create Data Reference"
    :submit-disabled="!isValid"
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
              @selection-cleared="containerChosen = false"
            />
          </v-col>
        </v-row>
        <v-row>
          <v-col class="pt-1">
            <MandatoryFieldHint />
          </v-col>
        </v-row>
        <v-row v-if="containerChosen">
          <v-col class="pt-9 pb-1">
            <div class="text-subtitle-1">Select Data</div>
          </v-col>
        </v-row>
        <v-row v-if="containerChosen">
          <v-col class="pt-1">
            <ReferenceTable
              v-if="displayItems"
              :items="displayItems"
              :loading="loading"
              @sended-oid-list="getDataOidList"
            />
          </v-col>
        </v-row>
      </v-form>
    </template>
  </FormDialog>
</template>
