<script setup lang="ts">
import Loading from "@/components/generic/Loading.vue";
import { useContainerSearch } from "@/components/search/useContainerSearch";
import { useUnifiedSearch } from "@/components/search/useUnifiedSearch";
import BasicReferenceService from "@/services/basicReferenceService";
import { logError } from "@/utils/error-handling";
import {
  getQueryParam,
  removeQueryParam,
  setQueryParam,
} from "@/utils/helpers";
import {
  ResponseError,
  SearchScopeTraversalRulesEnum,
  type BasicReference,
  type ResultTriple,
} from "@dlr-shepard/shepard-client";
import { useTitle } from "@vueuse/core";
import { default as JSONEditor, type JSONEditorOptions } from "jsoneditor";
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue2-helpers/vue-router";

const router = useRouter();
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
const selectedQueryType = ref<string>("Collection");

const queryTypeUnifiedSearch = [
  { value: "Collection", text: "Collection" },
  { value: "DataObject", text: "Data Object" },
  { value: "Reference", text: "Reference" },
  { value: "StructuredData", text: "StructuredData" },
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

const unifiedSearchParam = ref<{
  searchQuery: string | undefined;
  selectedQueryType: string | undefined;
  collectionId: number | undefined;
  dataObjectId: number | undefined;
  traversalRules: SearchScopeTraversalRulesEnum[];
}>({
  searchQuery: undefined,
  selectedQueryType: undefined,
  collectionId: undefined,
  dataObjectId: undefined,
  traversalRules: [],
});
const UnifiedSearchData = useUnifiedSearch(unifiedSearchParam);

const queryTypeContainerSearch = [
  { value: "FILE", text: "File Container" },
  { value: "STRUCTUREDDATA", text: "Structured Data Container" },
  { value: "TIMESERIES", text: "Timeseries Container" },
];
const containerSearchParam = ref<{
  searchQuery: string | undefined;
  selectedQueryType: string | undefined;
}>({
  searchQuery: undefined,
  selectedQueryType: undefined,
});
const containerSearchData = useContainerSearch(containerSearchParam);
const containerSearchFields = [
  { key: "id", label: "ID", sortable: true },
  { key: "name", label: "Name", sortable: true },
];

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
  if (editor.value) {
    editor.value.set(initialJson);
  }
  currentCollectionId.value = undefined;
  currentDataObjectId.value = undefined;
  selectedTraversalRules.value = [];
  selectedQueryType.value = "Collection";
  searchType.value = "";
  removeAllQueryParam();
}

const searchType = ref<"unified" | "container" | "">("");
function handleSearch() {
  // get actual json of the JsonEditor
  if (!editor.value) return;
  const searchQuery = JSON.stringify(editor.value.get());

  if (
    queryTypeUnifiedSearch.some(
      element => element.value == selectedQueryType.value,
    )
  ) {
    searchType.value = "unified";
    unifiedSearchParam.value = {
      searchQuery: searchQuery,
      selectedQueryType: selectedQueryType.value,
      collectionId: currentCollectionId.value,
      dataObjectId: currentDataObjectId.value,
      traversalRules: selectedTraversalRules.value,
    };
  } else if (
    queryTypeContainerSearch.some(
      element => element.value == selectedQueryType.value,
    )
  ) {
    searchType.value = "container";
    containerSearchParam.value = {
      searchQuery: searchQuery,
      selectedQueryType: selectedQueryType.value,
    };
  } else {
    searchType.value = "";
    return;
  }
  setAllQueryParam(searchQuery);
}

async function rowSelectedUnifiedSearch(item: ResultTriple) {
  let routeData = undefined;

  if (item.referenceId != undefined) {
    // A reference
    routeData = router.resolve({
      name: "DataObject",
      params: {
        collectionId: String(item.collectionId),
        dataObjectId: String(item.dataObjectId),
      },
    });
    const params = await getReferenceQueryParams(item);
    if (params) routeData.href += params;
  } else if (item.dataObjectId != undefined) {
    // A data object
    routeData = router.resolve({
      name: "DataObject",
      params: {
        collectionId: String(item.collectionId),
        dataObjectId: String(item.dataObjectId),
      },
    });
  } else {
    // a collection
    routeData = router.resolve({
      name: "Collection",
      params: {
        collectionId: String(item.collectionId),
      },
    });
  }
  openLink(routeData.href);
}

async function getReferenceQueryParams(item: ResultTriple) {
  const tabMapping: { [key: string]: number } = {
    TimeseriesReference: 0,
    StructuredDataReference: 1,
    FileReference: 2,
    URIReference: 3,
    CollectionReference: 4,
    DataObjectReference: 5,
  };
  const referenceType = await getReferenceType(item);
  if (!referenceType) return;
  return (
    "?tabId=" + tabMapping[referenceType] + "&referenceId=" + item.referenceId
  );
}

async function getReferenceType(
  item: ResultTriple,
): Promise<string | undefined> {
  if (!item.collectionId || !item.dataObjectId || !item.referenceId) return;
  let reference: BasicReference;
  try {
    reference = await BasicReferenceService.getBasicReference({
      collectionId: item.collectionId,
      dataObjectId: item.dataObjectId,
      referenceId: item.referenceId,
    });
  } catch (error) {
    logError(error as ResponseError, "fetching basic reference");
    return;
  }

  return reference.type;
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
  selectedQueryType.value = queryType ? queryType : "Collection";

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

function removeAllQueryParam() {
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
  <div>
    <div class="component">
      <h4 class="mb-5">Search</h4>
      <b-container>
        <b-row>
          <b-col>
            <b-row class="mb-2">
              <b-col cols="4">Query Type</b-col>
              <b-col cols="8">
                <b-form-select v-model="selectedQueryType">
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
            <div
              v-if="
                UnifiedSearchData.results.value?.resultSet &&
                UnifiedSearchData.results.value?.resultSet.length > 0
              "
            >
              <b-table
                sticky-header="766px"
                head-variant="light"
                striped
                hover
                class="table table-sm table-bordered"
                :items="UnifiedSearchData.results.value?.resultSet"
                @row-clicked="rowSelectedUnifiedSearch($event)"
              >
              </b-table>
            </div>
            <div
              v-else-if="
                containerSearchData.results.value &&
                containerSearchData.results.value.length > 0
              "
            >
              <b-table
                sticky-header="766px"
                head-variant="light"
                striped
                hover
                class="table table-sm table-bordered"
                :items="containerSearchData.results.value"
                :fields="containerSearchFields"
                @row-clicked="openLink($event.link)"
              >
              </b-table>
            </div>
            <div v-else class="pl-2">no results</div>

            <Loading
              v-if="
                UnifiedSearchData.loading.value ||
                containerSearchData.loading.value
              "
            />
          </b-col>
        </b-row>
      </b-container>
    </div>
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
