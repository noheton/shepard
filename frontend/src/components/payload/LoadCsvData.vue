<script setup lang="ts">
import type { ResponseError } from "@/generated/openapi";
import FileService from "@/services/fileService";
import { logError } from "@/utils/error-handling";
import { Chart, registerables } from "chart.js";
import {
  parse,
  type CastingContext,
  type CsvError,
} from "csv-parse/browser/esm";
import { onMounted, reactive, ref } from "vue";

Chart.register(...registerables);

const props = defineProps({
  containerId: {
    type: Number,
    required: true,
  },
  oid: {
    type: String,
    required: true,
  },
});

const emit = defineEmits(["parsed-data", "parsing-error"]);

const getInitialFormData = () => ({
  startLine: "0",
  delimiter: ";",
  header: true,
  decimalComma: true,
  skipRowsAfterHeader: "0",
});

const maxNumberOfColumns = 50;

const formData = reactive(getInitialFormData());
const parsingWentWrong = ref<boolean>(false);
const errorType = ref<string>("");
const fileNotFound = ref<boolean>(false);
const csvFileData = ref<string>();

function reset() {
  Object.assign(formData, getInitialFormData());
  parsingWentWrong.value = false;
  errorType.value = "";
  fileNotFound.value = false;
  csvFileData.value = undefined;
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

function parser() {
  let delimiterForParsing = formData.delimiter;
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
        errorType.value = err
          ? err.name + ": " + err.message
          : "Undefined Error";
        emit("parsing-error");
      } else if (!records.toString()) {
        parsingWentWrong.value = true;
        errorType.value = "File is empty";
        emit("parsing-error");
      } else {
        parsingWentWrong.value = false;
        processData(records);
      }
    },
  );
}

function applyDecimalComma(value: string, context: CastingContext) {
  if (formData.decimalComma === true && !context.header) {
    return value.replace(",", ".");
  }
  return value;
}

function processData(records: string[][]) {
  const columnNames = updateColumnNames(records);
  const slicedRecords = sliceRecords(records);
  const formattedRecords = formatRecords(slicedRecords, columnNames);
  emit("parsed-data", formattedRecords);
}

function updateColumnNames(records: string[][]): string[] {
  const startLineValue = +formData.startLine;
  if (formData.header === true) {
    // if a header is existing
    return records[startLineValue];
  } else {
    // if no head is existing create some names
    return createColumnNames(records[startLineValue].length);
  }
}

function sliceRecords(records: string[][]): string[][] {
  const startLineValue = +formData.startLine;
  const skipRowsAfterHeaderValue = +formData.skipRowsAfterHeader;
  // here are the header and lines after header important (GUI input values)
  return records.slice(
    startLineValue + skipRowsAfterHeaderValue + 1,
    records.length,
  );
}

function formatRecords(
  records: string[][],
  columnNames: string[],
): Map<string, string>[] {
  const formattedRecords = [];
  const numberOfColumns =
    columnNames.length > maxNumberOfColumns
      ? maxNumberOfColumns
      : columnNames.length;

  // iterate over the head of the data in order to display them
  for (let rowNumber = 0; rowNumber < records.length; rowNumber++) {
    const row = new Map<string, string>();
    const rowValue = records[rowNumber];
    for (let colNumber = 0; colNumber < numberOfColumns; colNumber++) {
      const columName = columnNames[colNumber];
      if (rowValue && rowValue[colNumber]) {
        row.set(columName, rowValue[colNumber]);
      }
    }
    formattedRecords.push(row);
  }
  return formattedRecords;
}

function createColumnNames(count: number) {
  const nameBasis = "col";
  const colNames: string[] = Array.from(
    { length: count },
    (_, i) => nameBasis + (i + 1),
  );
  return colNames;
}

onMounted(() => {
  reset();
  fetchCsvFile();
});
</script>

<template>
  <div>
    <b-alert :show="fileNotFound" variant="danger">File not available</b-alert>
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
    <b-container>
      <b-form-group>
        <b-row class="mb-3">
          <b-col>
            <div>Start from Line:</div>
            <b-form-input
              v-model="formData.startLine"
              type="number"
            ></b-form-input>
          </b-col>
          <b-col>
            <div>Delimiter</div>
            <b-form-input
              v-model="formData.delimiter"
              type="text"
            ></b-form-input>
          </b-col>
        </b-row>
        <b-row class="mb-3">
          <b-col>
            <b-form-checkbox v-model="formData.header" unchecked-value="false">
              Header
            </b-form-checkbox>
          </b-col>
          <b-col>
            <b-form-checkbox
              v-model="formData.decimalComma"
              unchecked-value="false"
            >
              Decimal comma
            </b-form-checkbox>
          </b-col>
        </b-row>
        <b-row class="mb-1">
          <b-col>
            <div>Skip rows after header:</div>
            <b-form-input
              v-model="formData.skipRowsAfterHeader"
              type="number"
            ></b-form-input>
          </b-col>
        </b-row>
        <b-button
          :disabled="fileNotFound"
          variant="success"
          class="float-right"
          @click="parser()"
        >
          Parse Data
        </b-button>
      </b-form-group>
    </b-container>
  </div>
</template>
