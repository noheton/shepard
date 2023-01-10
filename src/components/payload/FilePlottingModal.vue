<script setup lang="ts">
import { HSVtoRGB } from "@/utils/colors";
import {
  Chart,
  registerables,
  type ChartData,
  type ScatterDataPoint,
} from "chart.js";
import {
  parse,
  type CastingContext,
  type CsvError,
} from "csv-parse/browser/esm";
import { ref } from "vue";
import { Scatter } from "vue-chartjs";

Chart.register(...registerables);

const chartOptions = {
  datasets: { scatter: { showLine: true, tension: 0.1 } },
  responsive: true,
  maintainAspectRatio: false,
  animation: {
    duration: 0,
  },
  scales: {
    x: {
      title: {
        display: true,
        text: "",
      },
    },
    y: {
      title: {
        display: true,
        text: "",
      },
    },
  },
};

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
const chartData = ref<
  ChartData<"scatter", (number | ScatterDataPoint | null)[], unknown>
>({ datasets: [] });
const recordsParsed = ref<string[][]>([]);
const columnNames = ref<string[]>([]);
const returnColor = ref<number[]>([]);
const colorCounter = ref<number>(0);
const plotShown = ref<boolean>(false);
const updated = ref(0);
const parsingWentWrong = ref<boolean>(false);
const errorType = ref<string>("");

const props = defineProps({
  modalId: {
    type: String,
    default: "PlottingModalCsv",
  },
  modalName: {
    type: String,
    default: "PlottingModalCsv",
  },
  csvData: {
    type: String,
    default: undefined,
  },
});

function reset() {
  header.value = true;
  decimalComma.value = true;
  startLine.value = "0";
  delimiter.value = ";";
  skipRowsAfterHeader.value = "0";
  dataForPreview.value = [];
  colorCounter.value = 0;
  plotShown.value = false;
  plottingOptionListX.value = [
    { value: "", text: "Please parse your data first", disabled: true },
  ];
  plottingOptionListY.value = [
    { value: "", text: "Please parse your data first", disabled: true },
  ];
  plottingSelectionX.value = "";
  plottingSelectionY.value = [];
  chartData.value = { datasets: [] };
  parsingWentWrong.value = false;
  errorType.value = "";
}

function parser() {
  recordsParsed.value = [];
  let delimiterForParsing = delimiter.value;
  if (delimiterForParsing === "\\t") {
    delimiterForParsing = "\t";
  }
  if (!props.csvData) {
    return;
  }
  parse(
    props.csvData,
    {
      delimiter: delimiterForParsing,
      cast: applyDecimalComma,
    },
    (err?: CsvError, records?: string[][]) => {
      if (err || !records) {
        console.log("Error");
        parsingWentWrong.value = true;
        errorType.value = err
          ? err.name + ": " + err.message
          : "Undefined Error";
      } else {
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
  for (let row = 0; row < 6; row++) {
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

function colorCalculator(counter: number) {
  const baseColorArray = [
    [202, 1, 0.733],
    [1, 0.659, 0.702],
  ];
  let colorIndex = 0;
  if (counter < 3) {
    returnColor.value = baseColorArray[colorIndex];
    returnColor.value[1] = returnColor.value[1] - counter * 0.4;
  } else {
    colorIndex = 1;
    returnColor.value = baseColorArray[colorIndex];
    returnColor.value[1] = returnColor.value[1] - (counter - 3) * 0.3;
    if (counter == 5) {
      colorCounter.value = -1;
    }
  }
  return HSVtoRGB(returnColor.value);
}

function visualizeCsvData() {
  colorCounter.value = 0;
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
    const colorSetting = colorCalculator(colorCounter.value);
    chartData.value.datasets.push({
      data: data,
      label: choiceY,
      fill: false,
      borderColor: colorSetting,
      backgroundColor: colorSetting,
    });
    colorCounter.value++;
  });
  plotShown.value = true;
  updated.value++;
}

function savePlot() {
  // saveData() inspired by https://github.com/apertureless/vue-chartjs/issues/89#issuecomment-292718708
  const plottingImage = document.getElementById(
    "scatter-chart",
  ) as HTMLCanvasElement | null;
  if (plottingImage != null) {
    const link = document.createElement("a");
    link.download = props.modalName.replace(/[<>:"/\\|?* ]/g, "_");
    link.href = plottingImage.toDataURL("image/png");
    link.click();
  }
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
    <b-alert
      :show="parsingWentWrong"
      variant="danger"
      dismissible
      @dismissed="parsingWentWrong = false"
    >
      <p>
        <b>{{ errorType }}</b>
      </p>
      <hr />
      <p class="mb-0">Please check if:</p>
      <p class="mb-0">&#x2022; File contains data and is not empty</p>
      <p class="mb-0">&#x2022; You provided the right parser element</p>
      <p class="mb-0">
        &#x2022; File format is correct and the file is realy a csv file
      </p>
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
              id="checkCommaConvenation"
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
            variant="success"
            @click="visualizeCsvData()"
          >
            Show Plot
          </b-button>
          <b-button v-else :disabled="true" variant="success">
            Show Plot
          </b-button>
          <b-button
            id="exportButton"
            v-b-tooltip.hover
            title="Save Plot to .PNG"
            variant="secondary"
            class="ml-1"
            :disabled="!plotShown"
            @click="savePlot()"
          >
            Save Plot
          </b-button>
        </b-col>
      </b-row>
    </b-container>
    <div class="plot">
      <Scatter
        v-if="chartData.datasets.length > 0"
        :key="updated"
        :options="chartOptions"
        :data="chartData"
        chart-id="scatter-chart"
        dataset-id-key="label"
      />
    </div>
  </b-modal>
</template>

<style scoped>
.plot {
  height: 400px;
  position: "relative";
}
</style>
