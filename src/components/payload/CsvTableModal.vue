<script setup lang="ts">
import LoadCsvData from "@/components/payload/LoadCsvData.vue";
import { Chart, registerables } from "chart.js";
import { ref } from "vue";

Chart.register(...registerables);

const props = defineProps({
  modalId: {
    type: String,
    default: "PlottingModalCsv",
  },
  modalName: {
    type: String,
    default: "PlottingModalCsv",
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

const data = ref<{ [key: string]: string }[]>([]);

function reset() {
  data.value = [];
}

function handleParsedCsvData(parsedCsvData: { [key: string]: string }[]) {
  data.value = parsedCsvData;
}
</script>

<template>
  <b-modal
    :id="props.modalId"
    ref="modal"
    size="xl"
    :title="props.modalName"
    lazy
    ok-only
    ok-title="Close"
    no-close-on-backdrop
    @show="reset()"
  >
    <LoadCsvData
      :container-id="props.containerId"
      :oid="props.oid"
      @parsed-data="handleParsedCsvData($event)"
      @parsing-error="reset()"
    />

    <b-table
      sticky-header
      class="text-nowrap"
      responsive
      striped
      hover
      small
      :items="data"
    >
    </b-table>
  </b-modal>
</template>
