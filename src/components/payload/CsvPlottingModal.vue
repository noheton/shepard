<script setup lang="ts">
import LoadCsvData from "@/components/payload/LoadCsvData.vue";
import VisualizationModal from "@/components/payload/VisualizationModal.vue";
import type { PlottingData } from "@/utils/plotting";
import { Chart, registerables } from "chart.js";
import { ref } from "vue";

Chart.register(...registerables);

interface selectOption {
  value: string;
  text: string;
  disabled?: boolean;
}

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

const maxObjects = 5;
const getInitialSelectOption = () => ({
  value: "",
  text: "Please parse your data first",
  disabled: true,
});
const getInitialPlottingData = () => ({ datasets: [], xLabel: "x Value" });

const plottingOptionListX = ref<selectOption[]>([]);
const plottingOptionListY = ref<selectOption[]>([]);
const dataForPreview = ref<{ [key: string]: string }[]>([]);
const recordsParsed = ref<{ [key: string]: string }[]>([]);
const plottingSelectionX = ref<string>("");
const plottingSelectionY = ref<string[]>([]);
const plottingData = ref<PlottingData>(getInitialPlottingData());
const columnNames = ref<string[]>([]);
const plotShown = ref<boolean>(false);
const updated = ref(0);

function reset() {
  dataForPreview.value = [];
  plottingOptionListX.value = [getInitialSelectOption()];
  plottingOptionListY.value = [getInitialSelectOption()];
  plottingSelectionX.value = "";
  plottingSelectionY.value = [];
  plottingData.value = getInitialPlottingData();
  columnNames.value = [];
  plotShown.value = false;
}

function handleParsedCsvData(parsedCsvData: { [key: string]: string }[]) {
  recordsParsed.value = parsedCsvData;
  dataForPreview.value = parsedCsvData.slice(0, maxObjects);
  if (!dataForPreview.value) {
    console.log("here");
    plottingOptionListX.value = [
      { value: "", text: "Please parse your data first", disabled: true },
    ];
    plottingOptionListY.value = [
      { value: "", text: "Please parse your data first", disabled: true },
    ];
  } else {
    columnNames.value = Object.keys(dataForPreview.value[0]);
    plottingOptionListX.value = updatePlottingOptions(columnNames.value);
    plottingOptionListY.value = updatePlottingOptions(columnNames.value);
  }
  console.log(dataForPreview.value);
  console.log(plottingOptionListX.value);
  console.log(plottingOptionListY.value);
}

function updatePlottingOptions(
  columnNames: string[],
): { value: string; text: string }[] {
  return columnNames.map(element => {
    return {
      value: element,
      text: element,
    };
  });
}

function createPlottableData() {
  plottingData.value.datasets = [];
  const choiceX = plottingSelectionX.value;
  const choicesY = plottingSelectionY.value;
  choicesY.forEach(choiceY => {
    const data = recordsParsed.value.map(row => {
      return {
        x: parseFloat(row[choiceX]),
        y: parseFloat(row[choiceY]),
      };
    });
    plottingData.value.datasets.push({
      dataPoints: data,
      label: choiceY,
    });
  });
  plotShown.value = true;
  updated.value++;
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
    <b-container>
      <LoadCsvData
        :container-id="props.containerId"
        :oid="props.oid"
        @parsed-data="handleParsedCsvData($event)"
        @parsing-error="reset()"
      />
      <b-table
        class="text-nowrap"
        responsive
        striped
        hover
        small
        :items="dataForPreview"
      >
      </b-table>
      <b-row class="mb-1">
        <b-col>
          <div>Select one x-value:</div>
          <b-form-select
            v-model="plottingSelectionX"
            :options="plottingOptionListX"
            :select-size="6"
          ></b-form-select>
        </b-col>
        <b-col>
          <div>Select y-value(s):</div>
          <b-form-select
            v-model="plottingSelectionY"
            :options="plottingOptionListY"
            multiple
            :select-size="6"
          >
          </b-form-select>
        </b-col>
      </b-row>
      <b-button
        v-b-modal.visualization
        :disabled="
          plottingSelectionX.length == 0 || plottingSelectionY.length == 0
        "
        variant="success"
        class="float-right"
        @click="createPlottableData()"
      >
        Show Plot
      </b-button>
    </b-container>
    <VisualizationModal
      v-if="plottingData.datasets.length > 0"
      modal-id="visualization"
      :modal-name="'Visualization of ' + props.modalName"
      :input-data="plottingData"
    />
  </b-modal>
</template>
