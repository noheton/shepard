<script setup lang="ts">
/* eslint-disable @typescript-eslint/no-explicit-any */

import StructuredDataService from "@/services/structuredDataService";
import { handleError, logError } from "@/utils/error-handling";
import type {
  ResponseError,
  StructuredData,
  StructuredDataContainer,
  StructuredDataPayload,
  StructuredDataReference,
} from "@dlr-shepard/shepard-client";
import JSONEditor, { type JSONEditorOptions } from "jsoneditor";
import { reactive, ref } from "vue";

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

const emit = defineEmits(["create"]);

const getInitialFormData = () => ({
  newStructuredDataReferenceName: "",
  newContainerName: "",
  existingContainerId: "",
  currentContainer: undefined,
  validContainer: undefined,
  possibleOids: [],
  selectedOids: [],
  containerSelection: "useExistingContainer",
  structuredDataSelection: "chooseUploadedStructuredData",
});

const formData = reactive<{
  newStructuredDataReferenceName: string;
  newContainerName: string;
  existingContainerId: string;
  currentContainer: StructuredDataContainer | undefined;
  validContainer: boolean | undefined;
  possibleOids: Option[];
  selectedOids: string[];
  containerSelection: string;
  structuredDataSelection: string;
}>(getInitialFormData());

const newStructuredDataName = ref<string>("");

const jsoneditor = ref<JSONEditor>();

function handleReset() {
  Object.assign(formData, getInitialFormData());
  jsoneditor.value = undefined;
}

function startJsonEditor() {
  // create the editor
  const container = document.getElementById("jsoneditor");
  const options: JSONEditorOptions = {
    mode: "tree",
    modes: ["code", "tree"], // allowed modes
  };
  if (container) {
    jsoneditor.value = new JSONEditor(container, options);
  } else {
    jsoneditor.value = undefined;
  }
}

function fetchContainer() {
  if (formData.existingContainerId)
    StructuredDataService.getStructuredDataContainer({
      structureddataContainerId: +formData.existingContainerId,
    })
      .then(container => {
        formData.currentContainer = container;
        formData.validContainer = true;
        fetchStructuredData();
      })
      .catch(e => {
        logError(e as ResponseError, "fetching structured data container");
        formData.currentContainer = undefined;
        formData.validContainer = false;
      });
}

function fetchStructuredData() {
  if (formData.existingContainerId)
    StructuredDataService.getAllStructuredDatas({
      structureddataContainerId: +formData.existingContainerId,
    })
      .then(response => {
        response.forEach(structuredData => {
          if (!structuredData.oid || !formData.possibleOids) {
            return;
          }
          const option: Option = {
            value: structuredData.oid,
            text: structuredData.oid + " - " + structuredData.name,
          };
          formData.possibleOids.push(option);
        });
      })
      .catch(e => {
        handleError(e as ResponseError, "fetching all structured datas");
      });
}

async function handleOk() {
  const containerId = await getStructurdDataContainerId();
  if (!containerId) return;
  const oids = await getStructurdDataOids(containerId);

  const newStructuredDataReference: StructuredDataReference = {
    name: formData.newStructuredDataReferenceName,
    structuredDataContainerId: containerId,
    structuredDataOids: oids,
  };
  emit("create", newStructuredDataReference);
}

async function getStructurdDataContainerId() {
  let containerId = undefined;
  if (formData.containerSelection == "useExistingContainer") {
    containerId = +formData.existingContainerId;
  } else {
    const createdContainer = await createNewStructuredDataContainer(
      formData.newContainerName,
    );
    if (createdContainer) containerId = createdContainer.id;
  }
  return containerId;
}

async function getStructurdDataOids(containerId: number) {
  let oids = undefined;
  if (
    formData.containerSelection == "useExistingContainer" &&
    formData.structuredDataSelection == "chooseUploadedStructuredData"
  ) {
    oids = formData.selectedOids;
  } else {
    const structuredDataPayload: StructuredDataPayload = {
      structuredData: { name: newStructuredDataName.value },
      payload: JSON.stringify(jsoneditor.value?.get()),
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
    handleError(e as ResponseError, "creating structurd data container");
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
      @shown="startJsonEditor()"
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
                  v-model="formData.existingContainerId"
                  placeholder="Structured Data container id"
                  type="number"
                  required
                  :state="formData.validContainer"
                  @blur="fetchContainer()"
                ></b-form-input>
                <small v-if="formData.currentContainer">
                  <em> {{ formData.currentContainer.name }} </em>
                </small>
                <small v-else>Please enter a valid container id</small>
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
                :options="formData.possibleOids"
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
              <div
                id="jsoneditor"
                class="mt-3"
                :hidden="
                  formData.structuredDataSelection != 'uploadNewStructuredData'
                "
              ></div>
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
