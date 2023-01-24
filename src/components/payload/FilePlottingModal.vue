<script setup lang="ts">
import VisualizationModal from "@/components/payload/VisualizationModal.vue";
import FileService from "@/services/fileService";
import { logError } from "@/utils/error-handling";
import type { PlottingData } from "@/utils/plotting";
import type { ResponseError } from "@dlr-shepard/shepard-client";
import { Chart, registerables } from "chart.js";
import {
  parse,
  type CastingContext,
  type CsvError,
} from "csv-parse/browser/esm";
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

const numberOfRows = 4;

const startLine = ref("");
const delimiter = ref("");
const header = ref<boolean>();
const decimalComma = ref<boolean>();
const skipRowsAfterHeader = ref("");
const dataForPreview = ref();
const plottingOptionListX = ref<
  {
    value: string;
    text: string;
    disabled?: boolean;
  }[]
>([]);
const plottingOptionListY = ref<
  {
    value: string;
    text: string;
    disabled?: boolean;
  }[]
>([]);
const plottingSelectionX = ref<string>("");
const plottingSelectionY = ref<string[]>([]);
const recordsParsed = ref<string[][]>([]);
const chartData = ref<PlottingData>({ datasets: [], xLabel: "" });
const columnNames = ref<string[]>([]);
const plotShown = ref<boolean>(false);
const updated = ref(0);
const parsingWentWrong = ref<boolean>(false);
const errorType = ref<string>("");
const fileNotFound = ref<boolean>(false);
const csvFileData = ref<string>();

function reset() {
  startLine.value = "0";
  delimiter.value = ";";
  header.value = true;
  decimalComma.value = true;
  skipRowsAfterHeader.value = "0";
  dataForPreview.value = [];
  plottingOptionListX.value = [
    { value: "", text: "Please parse your data first", disabled: true },
  ];
  plottingOptionListY.value = [
    { value: "", text: "Please parse your data first", disabled: true },
  ];
  plottingSelectionX.value = "";
  plottingSelectionY.value = [];
  chartData.value = { datasets: [], xLabel: "x Value" };
  columnNames.value = [];
  plotShown.value = false;
  parsingWentWrong.value = false;
  errorType.value = "";
  fileNotFound.value = false;
  csvFileData.value = undefined;
}

function parser() {
  recordsParsed.value = [];
  let delimiterForParsing = delimiter.value;
  if (delimiterForParsing === "\\t") {
    delimiterForParsing = "\t";
  }
  if (!csvFileData.value) {
    return;
  }
  parse(
    csvFileData.value,
    {
      delimiter: delimiterForParsing,
      cast: applyDecimalComma,
    },
    (err?: CsvError, records?: string[][]) => {
      if (err || !records) {
        parsingWentWrong.value = true;
        dataForPreview.value = [];
        plottingOptionListX.value = [
          { value: "", text: "Please parse your data first", disabled: true },
        ];
        plottingOptionListY.value = [
          { value: "", text: "Please parse your data first", disabled: true },
        ];
        errorType.value = err
          ? err.name + ": " + err.message
          : "Undefined Error";
      } else {
        parsingWentWrong.value = false;
        updatePlottingList(records);
      }
    },
  );
}

function applyDecimalComma(value: string, context: CastingContext) {
  if (decimalComma.value === true && !context.header) {
    return value.replace(",", ".");
  }
  return value;
}

function updatePlottingList(records: string[][]) {
  const skipRowsAfterHeaderValue = +skipRowsAfterHeader.value;
  const startLineValue = +startLine.value;

  if (header.value === true) {
    // if a header is existing
    columnNames.value = records[startLineValue];
  } else {
    // if no head is existing create some names
    columnNames.value = createColumnNames(records[startLineValue].length);
  }

  // here are the header and lines after header important (GUI input values)
  recordsParsed.value = records.slice(
    startLineValue + skipRowsAfterHeaderValue + 1,
    records.length,
  );
  plottingOptionListX.value = updatePlottingOptions(columnNames.value);
  plottingOptionListY.value = updatePlottingOptions(columnNames.value);
  createParsingPreview();
}

function createParsingPreview() {
  dataForPreview.value = [];
  // iterate over the head of the data in order to display them
  for (let row = 0; row < numberOfRows; row++) {
    const rowValue = recordsParsed.value[row];
    const previewContentStorage: { [key: string]: string } = {};
    for (let col = 0; col < columnNames.value.length; col++) {
      if (rowValue && rowValue[col])
        previewContentStorage[columnNames.value[col]] = rowValue[col];
    }
    dataForPreview.value.push(previewContentStorage);
  }
}

function createColumnNames(count: number) {
  const nameBasis = "col";
  const colNames: string[] = Array.from(
    { length: count },
    (_, i) => nameBasis + (i + 1),
  );
  return colNames;
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
  chartData.value.datasets = [];
  const choiceX = plottingSelectionX.value;
  const choicesY = plottingSelectionY.value;
  choicesY.forEach(choiceY => {
    const dataPositionYValue = columnNames.value.indexOf(choiceY);
    const dataPositionXValue = columnNames.value.indexOf(choiceX);
    const data = recordsParsed.value.map(row => {
      return {
        x: parseFloat(row[dataPositionXValue]),
        y: parseFloat(row[dataPositionYValue]),
      };
    });
    chartData.value.datasets.push({
      dataPoints: data,
      label: choiceY,
    });
  });
  plotShown.value = true;
  updated.value++;
}

function fetchCsvFile() {
  const blobReader = new FileReader();
  FileService.getFile({
    fileContainerId: props.containerId,
    oid: props.oid,
  })
    .then(response => {
      blobReader.readAsText(response, "utf8");
      blobReader.addEventListener("load", () => {
        if (typeof blobReader.result === "string") {
          csvFileData.value = blobReader.result;
        }
      });
    })
    .catch(e => {
      logError(e as ResponseError, "fetching file payload");
      fileNotFound.value = true;
    });
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
    @show="
      reset();
      fetchCsvFile();
    "
  >
    <b-alert :show="fileNotFound" variant="danger"> File not found </b-alert>
    <b-alert
      :show="parsingWentWrong"
      variant="danger"
      @dismissed="parsingWentWrong = false"
    >
      {{ errorType }}
      <hr />
      <p class="mb-0">Please check if:</p>
      <p class="mb-0">&#x2022; File contains data and is not empty</p>
      <p class="mb-0">&#x2022; You provided the right parser element</p>
      <p class="mb-0">&#x2022; File format is correct</p>
    </b-alert>
    <b-form-group>
      <b-container>
        <b-row class="mb-4">
          <b-col>
            <div>Start from Line:</div>
            <b-form-input v-model="startLine" type="number"></b-form-input>
          </b-col>
          <b-col>
            <div>Delimiter</div>
            <b-form-input v-model="delimiter" type="text"></b-form-input>
          </b-col>
        </b-row>
        <b-row class="mb-4">
          <b-col>
            <b-form-checkbox
              id="checkHeader"
              v-model="header"
              unchecked-value="false"
            >
              Header
            </b-form-checkbox>
          </b-col>
          <b-col>
            <b-form-checkbox
              id="checkCommaConvention"
              v-model="decimalComma"
              unchecked-value="false"
            >
              Decimal comma
            </b-form-checkbox>
          </b-col>
        </b-row>
        <b-row class="mb-4">
          <b-col>
            <div>Skip rows after header:</div>
            <b-form-input
              v-model="skipRowsAfterHeader"
              type="number"
            ></b-form-input>
          </b-col>
        </b-row>
        <b-button variant="success" class="mb-2" @click="parser()">
          Parse Data
        </b-button>
        <b-table
          class="text-nowrap"
          responsive
          striped
          hover
          :items="dataForPreview"
        >
        </b-table>
      </b-container>
    </b-form-group>
    <b-container>
      <b-row class="mb-1">
        <b-col>
          <div>Select one x-value:</div>
          <b-form-select
            v-model="plottingSelectionX"
            class="mb-1"
            :options="plottingOptionListX"
            :select-size="6"
          ></b-form-select>
        </b-col>
        <b-col>
          <div>Select y-value(s)</div>
          <b-form-select
            v-model="plottingSelectionY"
            class="mb-1"
            :options="plottingOptionListY"
            multiple
            :select-size="6"
          >
          </b-form-select>
        </b-col>
      </b-row>
      <b-row>
        <b-col>
          <b-button
            v-if="
              (plottingSelectionX.length != 0, plottingSelectionY.length != 0)
            "
            v-b-modal.visualization
            variant="success"
            @click="createPlottableData()"
          >
            Show Plot
          </b-button>
          <b-button v-else :disabled="true" variant="success">
            Show Plot
          </b-button>
        </b-col>
      </b-row>
    </b-container>
    <VisualizationModal
      v-if="chartData.datasets.length > 0"
      modal-id="visualization"
      :modal-name="'Visualization of ' + props.modalName"
      :input-data="chartData"
    />
  </b-modal>
</template>
