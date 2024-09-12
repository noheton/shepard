<script setup lang="ts">
import JsonEditor from "@/components/generic/JsonEditor.vue";
import StructuredDataService from "@/services/structuredDataService";
import { handleError } from "@/utils/error-handling";
import type { ResponseError } from "@dlr-shepard/backend-client";

import { ref } from "vue";

const props = defineProps({
  modalId: {
    type: String,
    default: "StructuredDataModal",
  },
  modalName: {
    type: String,
    default: "StructuredDataModal",
  },
  containerId: {
    type: Number,
    required: true,
  },
  oid: {
    type: String,
    required: true,
  },
});

const payload = ref<string>();
function retrievePayload() {
  StructuredDataService.getStructuredData({
    structuredDataContainerId: props.containerId,
    oid: props.oid,
  })
    .then(response => {
      if (response.payload) payload.value = response.payload;
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching structured data payload");
    });
}
</script>

<template>
  <div>
    <b-modal
      :id="modalId"
      ref="modal"
      :title="modalName"
      size="lg"
      lazy
      ok-only
      @show="retrievePayload()"
    >
      <JsonEditor v-if="payload" v-model="payload" read-only />
    </b-modal>
  </div>
</template>
