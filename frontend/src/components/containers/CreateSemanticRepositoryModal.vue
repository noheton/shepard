<script setup lang="ts">
import { semanticRepositoryOptions as sRepoOptions } from "@/utils/helpers";
import type { SemanticRepositoryType } from "@dlr-shepard/shepard-client";
import { ref } from "vue";

const props = defineProps({
  modalId: {
    type: String,
    default: "GenericCreateModal",
  },
  modalName: {
    type: String,
    default: "GenericCreateModal",
  },
});
const semanticRepositoryOptions = sRepoOptions;

const emit = defineEmits(["create"]);
const newObject = ref<{
  name: string;
  type: SemanticRepositoryType;
  endpoint: string;
}>({
  name: "",
  type: "SPARQL",
  endpoint: "",
});

function handlePrepare() {
  newObject.value = {
    name: "",
    type: "SPARQL",
    endpoint: "",
  };
}

function handleOK() {
  newObject.value.endpoint = newObject.value.endpoint.trim();
  emit("create", newObject.value);
}
</script>

<template>
  <b-modal
    :id="props.modalId"
    ref="modal"
    size="lg"
    :title="props.modalName"
    lazy
    @show="handlePrepare()"
    @ok="handleOK()"
  >
    <b-form-group>
      <b-container>
        <b-row class="mb-3">
          <b-col cols="3"> Name </b-col>
          <b-col cols="9">
            <b-form-input
              v-model="newObject.name"
              placeholder="Name"
              required
            ></b-form-input>
          </b-col>
        </b-row>

        <b-row class="mb-3">
          <b-col cols="3"> Type </b-col>
          <b-col cols="9">
            <b-form-select
              v-model="newObject.type"
              :options="semanticRepositoryOptions"
            ></b-form-select>
          </b-col>
        </b-row>

        <b-row class="mb-3">
          <b-col cols="3"> Endpoint </b-col>
          <b-col cols="9">
            <b-form-input
              v-model="newObject.endpoint"
              placeholder="Name"
              required
            ></b-form-input>
          </b-col>
        </b-row>
      </b-container>
    </b-form-group>
  </b-modal>
</template>
