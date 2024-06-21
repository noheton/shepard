<script setup lang="ts">
/* eslint-disable @typescript-eslint/no-explicit-any */
import EntitySelectionPopover from "@/components/generic/EntitySelectionPopover.vue";
import JsonEditor from "@/components/generic/JsonEditor.vue";
import StructuredDataService from "@/services/structuredDataService";
import { handleError, logError } from "@/utils/error-handling";
import { isNumeric } from "@/utils/helpers";
import type {
  BasicEntity,
  ResponseError,
  StructuredData,
  StructuredDataContainer,
  StructuredDataPayload,
  StructuredDataReference,
} from "@dlr-shepard/shepard-client";
import { refDebounced } from "@vueuse/core";
import { reactive, ref } from "vue";
import { useSearchContainers } from "../search/InlineSearchContainers";

defineProps({
  modalId: {
    type: String,
    default: "StructuredDataReferenceModal",
  },
  modalName: {
    type: String,
    default: "StructuredDataReferenceModal",
  },
});

interface Option {
  value: string;
  text: string;
}

const userInputSearchContainer = ref("");
const userInputSearchContainerDebounced = refDebounced(
  userInputSearchContainer,
  700,
);
const { results } = useSearchContainers(
  userInputSearchContainerDebounced,
  "STRUCTUREDDATA",
);

const emit = defineEmits(["create"]);

const possibleOids = ref<Option[]>();
const currentContainer = ref<StructuredDataContainer>();
const validContainer = ref<boolean>();

const getInitialFormData = () => ({
  currentContainerId: "",
  newContainerName: "",
  newStructuredDataReferenceName: "",
  containerSelection: "useExistingContainer",
  structuredDataSelection: "chooseUploadedStructuredData",
  selectedOids: new Array<string>(),
});

const formData = reactive(getInitialFormData());

const newStructuredDataName = ref<string>("");
const jsonPayload = ref<string>("");

function handleReset() {
  Object.assign(formData, getInitialFormData());
  possibleOids.value = undefined;
  currentContainer.value = undefined;
  validContainer.value = undefined;
  userInputSearchContainer.value = "";
  jsonPayload.value = "";
}

function chooseContainer(container: BasicEntity) {
  if (!container.id) return;
  userInputSearchContainer.value = String(container.id);
  formData.currentContainerId = String(container.id);
  fetchContainer(container.id);
}

function fetchContainer(id: number) {
  StructuredDataService.getStructuredDataContainer({
    structureddataContainerId: id,
  })
    .then(container => {
      currentContainer.value = container;
      validContainer.value = true;
      fetchStructuredData(id);
    })
    .catch(e => {
      logError(e as ResponseError, "fetching structured data container");
      currentContainer.value = undefined;
      validContainer.value = false;
      possibleOids.value = [];
    });
}

function fetchStructuredData(id: number) {
  possibleOids.value = [];
  StructuredDataService.getAllStructuredDatas({
    structureddataContainerId: id,
  })
    .then(response => {
      response.forEach(structuredData => {
        if (!structuredData.oid) return;
        const option: Option = {
          value: structuredData.oid,
          text: structuredData.oid + " - " + structuredData.name,
        };
        if (possibleOids.value) possibleOids.value.push(option);
      });
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching all structured datas");
    });
}

async function handleOk() {
  const containerId = await getStructuredDataContainerId();
  if (!containerId) return;
  const oids = await getStructuredDataOids(containerId);

  const newStructuredDataReference: StructuredDataReference = {
    name: formData.newStructuredDataReferenceName,
    structuredDataContainerId: containerId,
    structuredDataOids: oids,
  };
  emit("create", newStructuredDataReference);
}

async function getStructuredDataContainerId() {
  let containerId = undefined;
  if (formData.containerSelection == "useExistingContainer") {
    containerId = +userInputSearchContainer.value;
  } else {
    const createdContainer = await createNewStructuredDataContainer(
      formData.newContainerName,
    );
    if (createdContainer) containerId = createdContainer.id;
  }
  return containerId;
}

async function getStructuredDataOids(containerId: number) {
  let oids = undefined;
  if (
    formData.containerSelection == "useExistingContainer" &&
    formData.structuredDataSelection == "chooseUploadedStructuredData"
  ) {
    oids = formData.selectedOids;
  } else {
    const structuredDataPayload: StructuredDataPayload = {
      structuredData: { name: newStructuredDataName.value },
      payload: jsonPayload.value,
    };
    const createdStructuredDataPayload = await createStructuredData(
      structuredDataPayload,
      containerId,
    );
    oids = createdStructuredDataPayload?.oid
      ? [createdStructuredDataPayload.oid]
      : [];
  }
  return oids;
}

async function createNewStructuredDataContainer(newName: string) {
  let response = undefined;
  try {
    response = await StructuredDataService.createStructuredDataContainer({
      structuredDataContainer: { name: newName },
    });
  } catch (e: any) {
    handleError(e as ResponseError, "creating structured data container");
  }
  return response;
}

async function createStructuredData(
  newStructuredDataPayload: StructuredDataPayload,
  currentContainerId: number,
) {
  let response: StructuredData | undefined;
  try {
    response = await StructuredDataService.createStructuredData({
      structureddataContainerId: currentContainerId,
      structuredDataPayload: newStructuredDataPayload,
    });
  } catch (e: any) {
    handleError(e as ResponseError, "creating Structured Data");
  }
  return response;
}
</script>

<template>
  <div>
    <b-modal
      :id="modalId"
      ref="modal"
      size="lg"
      :title="modalName"
      lazy
      @show="handleReset()"
      @ok="handleOk()"
    >
      <b-form-group>
        <b-container>
          <b-row class="mb-4">
            <b-col>
              <b-form-input
                v-model="formData.newStructuredDataReferenceName"
                placeholder="My new reference name"
                required
              ></b-form-input>
            </b-col>
          </b-row>

          <b-row class="mb-2 ml-0">
            <b-form-radio-group v-model="formData.containerSelection">
              <b-form-radio value="useExistingContainer">
                use existing container
              </b-form-radio>
              <b-form-radio value="createNewContainer">
                create new container
              </b-form-radio>
            </b-form-radio-group>
          </b-row>

          <div v-if="formData.containerSelection == 'useExistingContainer'">
            <b-row class="mb-4">
              <b-col>
                <b-form-input
                  id="userFormInput"
                  v-model="userInputSearchContainer"
                  placeholder="Structured Data container id"
                  required
                  :state="validContainer"
                  @blur="
                    if (isNumeric(userInputSearchContainer))
                      fetchContainer(+userInputSearchContainer);
                  "
                ></b-form-input>
                <small v-if="currentContainer">
                  <em> {{ currentContainer.name }} </em>
                </small>
                <small v-else>Please enter a valid container id</small>
                <EntitySelectionPopover
                  :results="results"
                  title-text="search for structured data containers by name, username, id or description"
                  @selected="chooseContainer($event)"
                />
              </b-col>
            </b-row>

            <b-row class="mb-2 ml-0">
              <b-form-radio-group v-model="formData.structuredDataSelection">
                <b-form-radio value="chooseUploadedStructuredData">
                  choose uploaded structured data
                </b-form-radio>
                <b-form-radio value="uploadNewStructuredData">
                  upload new structured data
                </b-form-radio>
              </b-form-radio-group>
            </b-row>
          </div>

          <div v-if="formData.containerSelection == 'createNewContainer'">
            <b-row class="mb-4">
              <b-col>
                <b-form-input
                  v-model="formData.newContainerName"
                  placeholder="My new container name"
                  required
                ></b-form-input>
              </b-col>
            </b-row>

            <b-row class="mb-2 ml-0">
              <b-form-radio-group v-model="formData.structuredDataSelection">
                <b-form-radio value="chooseUploadedStructuredData" disabled>
                  choose uploaded structured data
                </b-form-radio>
                <b-form-radio value="uploadNewStructuredData">
                  upload new structured data
                </b-form-radio>
              </b-form-radio-group>
            </b-row>
          </div>

          <b-row
            v-if="
              formData.structuredDataSelection == 'chooseUploadedStructuredData'
            "
            class="mb-4"
          >
            <b-col>
              <b-form-select
                v-model="formData.selectedOids"
                :options="possibleOids"
                multiple
                required
              ></b-form-select>
              <small>Please select your structured datas</small>
            </b-col>
          </b-row>

          <div
            v-if="formData.structuredDataSelection == 'uploadNewStructuredData'"
            class="mt-4"
          >
            <b-row>
              <b-col>
                <b-form-input
                  v-model="newStructuredDataName"
                  variant="primary"
                  placeholder="My new structured data Name"
                  required
                ></b-form-input>
              </b-col>
            </b-row>
          </div>

          <b-row>
            <b-col>
              <JsonEditor
                v-model="jsonPayload"
                class="mt-3"
                :hidden="
                  formData.structuredDataSelection != 'uploadNewStructuredData'
                "
              />
            </b-col>
          </b-row>
        </b-container>
      </b-form-group>
    </b-modal>
  </div>
</template>

<style>
#jsoneditor {
  height: 400px;
}
</style>
