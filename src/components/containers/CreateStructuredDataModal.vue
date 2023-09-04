<script setup lang="ts">
import type { StructuredDataPayload } from "@dlr-shepard/shepard-client";
import JSONEditor, { type JSONEditorOptions } from "jsoneditor";
import { ref } from "vue";

defineProps({
  modalId: {
    type: String,
    default: "CreateStructuredDataModal",
  },
  modalName: {
    type: String,
    default: "CreateStructuredDataModal",
  },
});

const emit = defineEmits(["created"]);

const newStructuredDataPayload = ref<StructuredDataPayload>();
const newStructuredDataName = ref<string>();
const jsoneditor = ref<JSONEditor>();

function handlePrepare() {
  newStructuredDataPayload.value = {};
  newStructuredDataName.value = "";
  jsoneditor.value = undefined;
}

function handleOk() {
  newStructuredDataPayload.value = {
    structuredData: { name: newStructuredDataName.value },
    payload: "{}",
  };
  newStructuredDataPayload.value.payload = JSON.stringify(
    jsoneditor.value?.get(),
  );

  emit("created", newStructuredDataPayload.value);
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
</script>

<template>
  <div>
    <b-modal
      :id="modalId"
      ref="modal"
      size="lg"
      :title="modalName"
      @show="handlePrepare()"
      @shown="startJsonEditor()"
      @ok="handleOk()"
    >
      <b-form-group>
        <b-container>
          <b-row class="mb-3">
            <b-col cols="2"> Name </b-col>
            <b-col cols="10">
              <b-form-input
                v-model="newStructuredDataName"
                variant="primary"
                placeholder="Name"
                required
              >
              </b-form-input>
            </b-col>
          </b-row>

          <b-row class="mb-3">
            <b-col cols="2"> JSON </b-col>
            <b-col cols="10">
              <div id="jsoneditor"></div>
            </b-col>
          </b-row>
        </b-container>
      </b-form-group>
    </b-modal>
  </div>
</template>

<style>
#jsoneditor {
  height: 600px;
}
</style>
