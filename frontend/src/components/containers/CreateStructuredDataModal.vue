<script setup lang="ts">
import JsonEditor from "@/components/generic/JsonEditor.vue";
import type { StructuredDataPayload } from "@dlr-shepard/shepard-client";
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
const payload = ref<string>("");

const newStructuredDataPayload = ref<StructuredDataPayload>();
const newStructuredDataName = ref<string>();

function handlePrepare() {
  newStructuredDataPayload.value = {};
  newStructuredDataName.value = "";
  payload.value = "";
}

function handleOk() {
  newStructuredDataPayload.value = {
    structuredData: { name: newStructuredDataName.value },
    payload: payload.value,
  };

  emit("created", newStructuredDataPayload.value);
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
              <JsonEditor v-model="payload" />
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
