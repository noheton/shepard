<script setup lang="ts">
import JsonEditor from "@/components/generic/JsonEditor.vue";
import Loading from "@/components/generic/Loading.vue";
import { useCollectionSearch } from "@/components/search/CollectionSearch";
import { useContainerSearch } from "@/components/search/ContainerSearch";
import { useDataObjectSearch } from "@/components/search/DataObjectSearch";
import { useReferenceSearch } from "@/components/search/ReferenceSearch";
import ReteModal from "@/components/search/rete/ReteModal.vue";
import { useStructuredDataSearch } from "@/components/search/StructuredDataSearch";
import {
  getQueryParam,
  removeQueryParam,
  setQueryParam,
} from "@/utils/helpers";
import { TraversalRules } from "@dlr-shepard/shepard-client";
import { useTitle } from "@vueuse/core";
import { computed, onMounted, ref } from "vue";

const initialJson = JSON.stringify({
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
});

const jsonQuery = ref<string>(initialJson);
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
const traversalRuleOptions = Object.values(TraversalRules);
const selectedTraversalRules = ref<TraversalRules[]>([]);
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
  traversalRules?: TraversalRules[];
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

function reset() {
  jsonQuery.value = initialJson;
  currentCollectionId.value = undefined;
  currentDataObjectId.value = undefined;
  selectedTraversalRules.value = [];
  selectedQueryType.value = "";
  searchParam.value = { selectedQueryType: "" };
  removeAllQueryParams();
}

function handleSearch() {
  // get actual json of the JsonEditor
  if (!jsonQuery.value) return;

  searchParam.value = {
    searchQuery: jsonQuery.value,
    selectedQueryType: selectedQueryType.value,
    collectionId: currentCollectionId.value,
    dataObjectId: currentDataObjectId.value,
    traversalRules: selectedTraversalRules.value,
  };
  setAllQueryParam();
}

function openLink(link: string) {
  window.open(link, "_blank");
}

function setAllQueryParam() {
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
  setQueryParam("searchQuery", JSON.stringify(JSON.parse(jsonQuery.value)));
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
  jsonQuery.value = searchQuery
    ? JSON.stringify(JSON.parse(searchQuery))
    : initialJson;
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
              <JsonEditor v-model="jsonQuery" class="mb-2"></JsonEditor>
              <div>
                <b-button-group class="mb-2">
                  <b-button
                    v-b-modal.rete-modal
                    v-b-tooltip.hover
                    title="Graphical Query Editor"
                    variant="secondary"
                  >
                    Graph
                  </b-button>
                  <b-button variant="info" @click="reset()"> Reset </b-button>
                </b-button-group>
                <b-button
                  class="float-right"
                  variant="primary"
                  @click="handleSearch()"
                >
                  Search
                </b-button>
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
    <ReteModal
      modal-id="rete-modal"
      modal-name="Graphical Query Editor"
      :input="jsonQuery"
      @changed="e => (jsonQuery = e.value)"
    />
  </div>
</template>

<style>
#jsoneditor {
  height: 500px;
}

.result-header {
  background-color: var(--info);
  color: var(--white);
  font-size: 1.5em;
  font-weight: bold;
  border: solid thin var(--info);
  border-radius: 0.2rem;
}
</style>
