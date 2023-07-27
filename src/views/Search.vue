<script setup lang="ts">
import Loading from "@/components/generic/Loading.vue";
import { useCollectionSearch } from "@/components/search/CollectionSearch";
import { useContainerSearch } from "@/components/search/ContainerSearch";
import { useDataObjectSearch } from "@/components/search/DataObjectSearch";
import { useReferenceSearch } from "@/components/search/ReferenceSearch";
import { useStructuredDataSearch } from "@/components/search/StructuredDataSearch";
import {
  getQueryParam,
  removeQueryParam,
  setQueryParam,
} from "@/utils/helpers";
import { SearchScopeTraversalRulesEnum } from "@dlr-shepard/shepard-client";
import { useTitle } from "@vueuse/core";
import { default as JSONEditor, type JSONEditorOptions } from "jsoneditor";
import { computed, onMounted, ref } from "vue";

const initialJson = {
  OR: [
    {
      property: "name",
      operator: "contains",
      value: "My",
    },
    {
      NOT: {
        property: "id",
        operator: "gt",
        value: 12,
      },
    },
  ],
};

let startJson = initialJson;
const selectedQueryType = ref<string>("");

const queryTypeUnifiedSearch = [
  { value: "Collection", text: "Collection" },
  { value: "DataObject", text: "Data Object" },
  { value: "Reference", text: "Reference" },
  { value: "StructuredData", text: "StructuredData" },
];
const queryTypeContainerSearch = [
  { value: "FILE", text: "File Container" },
  { value: "STRUCTUREDDATA", text: "Structured Data Container" },
  { value: "TIMESERIES", text: "Timeseries Container" },
];
const currentCollectionId = ref<number>();
const currentDataObjectId = ref<number>();
const traversalRuleOptions = Object.values(SearchScopeTraversalRulesEnum);
const selectedTraversalRules = ref<SearchScopeTraversalRulesEnum[]>([]);
const traversalRulesDisabled = computed(() => {
  return scopeDisabled.value || !currentDataObjectId.value;
});
const scopeDisabled = computed(() => {
  return !["DataObject", "Reference", "StructuredData"].includes(
    selectedQueryType.value,
  );
});

const searchParam = ref<{
  selectedQueryType: string;
  searchQuery?: string;
  collectionId?: number;
  dataObjectId?: number;
  traversalRules?: SearchScopeTraversalRulesEnum[];
}>({ selectedQueryType: "" });
const collectionResults = useCollectionSearch(searchParam);
const dataObjectResults = useDataObjectSearch(searchParam);
const referenceResults = useReferenceSearch(searchParam);
const sdResults = useStructuredDataSearch(searchParam);
const containerResults = useContainerSearch(searchParam);

const resultsTableFields = [
  { key: "id", label: "ID", sortable: true },
  { key: "name", label: "Name", sortable: true },
];
const loading = computed(
  () =>
    collectionResults.loading.value ||
    dataObjectResults.loading.value ||
    referenceResults.loading.value ||
    sdResults.loading.value ||
    containerResults.loading.value,
);
const results = computed(() =>
  [
    collectionResults.results.value,
    dataObjectResults.results.value,
    referenceResults.results.value,
    sdResults.results.value,
    containerResults.results.value,
  ].find(x => x.length > 0),
);

const editor = ref<JSONEditor>();
function jsonEditor() {
  const options: JSONEditorOptions = {
    mode: "tree",
    modes: ["code", "tree"],
    search: false,
  };
  // create the editor
  const container = document.getElementById("jsoneditor");
  if (container) {
    editor.value = new JSONEditor(container, options);
  } else {
    editor.value = undefined;
  }
  if (editor.value) {
    editor.value.set(startJson);
  }
}

function reset() {
  editor.value?.set(initialJson);
  currentCollectionId.value = undefined;
  currentDataObjectId.value = undefined;
  selectedTraversalRules.value = [];
  selectedQueryType.value = "";
  searchParam.value = { selectedQueryType: "" };
  removeAllQueryParams();
}

function handleSearch() {
  // get actual json of the JsonEditor
  if (!editor.value) return;
  const searchQuery = JSON.stringify(editor.value.get());

  searchParam.value = {
    searchQuery: searchQuery,
    selectedQueryType: selectedQueryType.value,
    collectionId: currentCollectionId.value,
    dataObjectId: currentDataObjectId.value,
    traversalRules: selectedTraversalRules.value,
  };
  setAllQueryParam(searchQuery);
}

function openLink(link: string) {
  window.open(link, "_blank");
}

function setAllQueryParam(searchQuery: string) {
  setQueryParam("queryType", String(selectedQueryType.value));
  if (currentCollectionId.value)
    setQueryParam("collectionId", String(currentCollectionId.value));
  if (currentDataObjectId.value)
    setQueryParam("dataObjectId", String(currentDataObjectId.value));
  if (selectedTraversalRules.value.length > 0)
    setQueryParam(
      "traversalRules",
      JSON.stringify(selectedTraversalRules.value),
    );
  setQueryParam("searchQuery", searchQuery);
}

function getAllQueryParam() {
  const queryType = getQueryParam("queryType");
  selectedQueryType.value = queryType ? queryType : "";

  const collectionId = getQueryParam("collectionId");
  currentCollectionId.value = collectionId ? Number(collectionId) : undefined;

  const dataObjectId = getQueryParam("dataObjectId");
  currentDataObjectId.value = dataObjectId ? Number(dataObjectId) : undefined;

  const traversalRules = getQueryParam("traversalRules");
  selectedTraversalRules.value = traversalRules
    ? JSON.parse(traversalRules)
    : [];

  const searchQuery = getQueryParam("searchQuery");
  startJson = searchQuery ? JSON.parse(searchQuery) : initialJson;
}

function removeAllQueryParams() {
  [
    "queryType",
    "collectionId",
    "dataObjectId",
    "traversalRules",
    "searchQuery",
  ].forEach(qp => removeQueryParam(qp));
}

onMounted(() => {
  useTitle("Search | shepard");
  getAllQueryParam();
  jsonEditor();
});
</script>

<template>
  <div class="view">
    <h4 class="mb-5">Search</h4>
    <b-container>
      <b-row>
        <b-col>
          <b-row class="mb-2">
            <b-col cols="4">Query Type</b-col>
            <b-col cols="8">
              <b-form-select v-model="selectedQueryType">
                <b-form-select-option value="" />
                <b-form-select-option-group
                  label="Unified Search"
                  :options="queryTypeUnifiedSearch"
                >
                </b-form-select-option-group>
                <b-form-select-option-group
                  label="Container Search"
                  :options="queryTypeContainerSearch"
                >
                </b-form-select-option-group>
              </b-form-select>
            </b-col>
          </b-row>

          <b-row class="mb-2">
            <b-col cols="4"> Collection ID </b-col>
            <b-col cols="8">
              <b-form-input
                v-model="currentCollectionId"
                :disabled="scopeDisabled"
                type="number"
                placeholder="Collection ID"
              >
              </b-form-input>
            </b-col>
          </b-row>
          <b-row class="mb-2">
            <b-col cols="4"> DataObject ID </b-col>
            <b-col cols="8">
              <b-form-input
                v-model="currentDataObjectId"
                :disabled="scopeDisabled"
                type="number"
                placeholder="DataObject ID"
              >
              </b-form-input>
            </b-col>
          </b-row>
          <b-row class="mb-2">
            <b-col cols="4">Traversal Rules</b-col>
            <b-col cols="5">
              <b-form-group>
                <b-form-checkbox-group
                  v-model="selectedTraversalRules"
                  :disabled="traversalRulesDisabled"
                  :options="traversalRuleOptions"
                >
                </b-form-checkbox-group>
              </b-form-group>
            </b-col>
          </b-row>
          <b-row>
            <b-col>
              <div id="jsoneditor" ref="jsoneditor"></div>
              <div>
                <b-button-group class="float-right mt-2 mb-2">
                  <b-button variant="info" @click="reset()"> Reset </b-button>
                  <b-button variant="primary" @click="handleSearch()">
                    Search
                  </b-button>
                </b-button-group>
              </div>
            </b-col>
          </b-row>
        </b-col>

        <b-col>
          <div class="pl-2 result-header">Result</div>
          <Loading v-if="loading" />
          <div v-else-if="results && results.length > 0">
            <b-table
              sticky-header="766px"
              head-variant="light"
              striped
              hover
              class="table table-sm table-bordered"
              :items="results"
              :fields="resultsTableFields"
              @row-clicked="openLink($event.link)"
            >
            </b-table>
          </div>
          <div v-else class="pl-2">no results</div>
        </b-col>
      </b-row>
    </b-container>
  </div>
</template>

<style scoped>
#jsoneditor {
  height: 500px;
}

.result-header {
  background-color: var(--info);
  color: var(--white);
  font-size: 1.5em;
  font-weight: bold;
  border: 1px solid;
}
</style>
