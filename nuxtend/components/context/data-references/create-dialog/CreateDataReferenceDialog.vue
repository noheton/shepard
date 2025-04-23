<script setup lang="ts">
import {
  FileContainerApi,
  FileReferenceApi,
  SpatialDataContainerApi,
  StructuredDataContainerApi,
  TimeseriesContainerApi,
  type ContainerType,
  type ResponseError,
  type ShepardFile,
} from "@dlr-shepard/backend-client";

interface CreateDataObjectDialogProps {
  collectionId: number;
  dataObjectId: number;
}
const props = defineProps<CreateDataObjectDialogProps>();
const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});
const emit = defineEmits(["data-reference-created"]);

const router = useRouter();

const dataReferenceName = ref<string>("");
const dataReferenceContainerId = ref<number | null>(null);
const files = ref<ShepardFile[]>([]);
const fileOids = ref<Array<string>>([""]);
const loading = ref<boolean>(false);

const isValid = ref<boolean>(true);
const form = useTemplateRef("form");
const containerChosen = ref<boolean>(false);

watch(dataReferenceContainerId, () => {
  if (dataReferenceContainerId.value) containerChosen.value = true;
  if (!dataReferenceContainerId.value) containerChosen.value = false;
});

async function createFileReference() {
  if (!dataReferenceContainerId.value) return;
  createApiInstance(FileReferenceApi)
    .createFileReference({
      collectionId: props.collectionId,
      dataObjectId: props.dataObjectId,
      fileReference: {
        name: dataReferenceName.value,
        fileContainerId: dataReferenceContainerId.value,
        fileOids: fileOids.value,
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
        handleError(e as ResponseError, "get File Container");
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
        handleError(e as ResponseError, "get Timeseries Container");
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
        handleError(e as ResponseError, "get Structured Data Container");
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
        handleError(e as ResponseError, "get Structured Data Container");
      });
  }
}

export interface DisplayItems extends ShepardFile {
  displayCreatedAt: string;
}
const displayItems = ref<DisplayItems[]>();
function getAllFiles(containerId: number) {
  fileContainerApi
    .getAllFiles({
      fileContainerId: containerId,
    })
    .then(response => {
      files.value = response;
      displayItems.value = files.value.map(d => {
        return { ...d, displayCreatedAt: toShortDateString(d.createdAt!)! };
      });
    })
    .catch(e => {
      handleError(e as ResponseError, "get all Files");
    });
}

function getAllStructuredDatas(containerId: number) {
  structuredDataContainerApi
    .getAllStructuredDatas({
      structuredDataContainerId: containerId,
    })
    .then(response => {
      console.log("not implemented yet" + response);
    })
    .catch(e => {
      handleError(e as ResponseError, "get all Structured Datas");
    });
}

function getFileOidList(oid: string[]) {
  fileOids.value = oid;
}
</script>

<template>
  <v-form ref="form" v-model="isValid">
    <Dialog
      v-model:show-dialog="showDialog"
      title="Create Data Reference"
      :submit-disabled="!isValid"
      @submit="createFileReference"
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
                @search-ended="getContainerById"
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
              <FileTable
                v-if="displayItems"
                :items="displayItems"
                :loading="loading"
                @sended-oid-list="getFileOidList"
              />
            </v-col>
          </v-row>
        </v-form>
      </template>
    </Dialog>
  </v-form>
</template>
