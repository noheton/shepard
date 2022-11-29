<script setup lang="ts">
import { HSVtoRGB } from "@/utils/colors";
import type { ChartData } from "chart.js";
import {
  parse,
  type CastingContext,
  type CsvError,
} from "csv-parse/browser/esm";
import { ref } from "vue";
import { Scatter } from "vue-chartjs/legacy";

const chartOptions = {
  datasets: { scatter: { showLine: true, tension: 0.1 } },
  responsive: true,
  maintainAspectRatio: false,
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
const skipRowsAfterHeader = ref("0");
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
const chartData = ref<ChartData>({ datasets: [] });
const recordsParsed = ref<{ [key: string]: string }[]>([]);
const columnNames = ref<string[]>([]);
const returnColor = ref<number[]>([]);
const colorCounter = ref<number>(0);
const plotShown = ref<boolean>();

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
    updatePlottingList,
  );
}

function applyDecimalComma(value: string, context: CastingContext) {
  if (decimalComma.value === true && !context.header) {
    return value.replace(",", ".");
  }
  return value;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function updatePlottingList(err?: CsvError, records?: any) {
  // if a header is existing
  if (header.value === true) {
    columnNames.value = records[parseInt(startLine.value)];
    plottingOptionListX.value = updatePlottingOptions(columnNames.value);
    plottingOptionListY.value = updatePlottingOptions(columnNames.value);
    // here are the header and lines after header important (GUI input values)
    recordsParsed.value = records.slice(
      startLine.value + skipRowsAfterHeader.value + 1,
      records.length,
    );
  }
  // if no head is existing create some names
  else {
    columnNames.value = createColumnNames(records[startLine.value].length);
    plottingOptionListX.value = updatePlottingOptions(columnNames.value);
    plottingOptionListY.value = updatePlottingOptions(columnNames.value);
    recordsParsed.value = records.slice(
      skipRowsAfterHeader.value,
      records.length,
    );
  }
  createParsingPreview();
}

function createParsingPreview() {
  dataForPreview.value = [];
  // iterate over the head of the data in order to display them
  for (let row = 0; row < 6; row++) {
    const rowValue = recordsParsed.value[row];
    const previewContentStorage: { [key: string]: string } = {};
    for (let col = 0; col < columnNames.value.length; col++) {
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
    colorCounter.value = colorCounter.value + 1;
    plotShown.value = true;
  });
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
    <Scatter
      v-if="chartData.datasets.length > 0"
      :chart-options="chartOptions"
      :chart-data="chartData"
      chart-id="scatter-chart"
      dataset-id-key="label"
    />
  </b-modal>
</template>
