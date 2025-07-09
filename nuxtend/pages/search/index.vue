<script lang="ts" setup>
import {
  ContainerType,
  QueryType,
  type SearchScope,
  type TraversalRules,
} from "@dlr-shepard/backend-client";
import { useTitle } from "@vueuse/core";
import type { SearchResult } from "~/components/context/Search/context/SearchResultList.vue";
import {
  search,
  SearchCollectionRequest,
  SearchDataObjectRequest,
  SearchFileContainerRequest,
  SearchReferenceRequest,
  SearchStructuredContainerRequest,
  SearchStructuredRequest,
  SearchTimeseriesContainerRequest,
} from "~/components/context/Search/context/searchService";
import type { QueryContainerType } from "~/components/context/Search/input-components/QueryTypeInput.vue";

const initialJson = JSON.stringify(
  {
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
  },
  null,
  4,
);

const jsonQuery = ref<string>(initialJson);
const isJsonQueryValid = computed(() => {
  try {
    JSON.parse(jsonQuery.value);
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
  } catch (_) {
    return false;
  }
  return true;
});
const selectedQueryType = ref<QueryContainerType>(QueryType.Collection);
const selectedCollectionId = ref<number>();
const selectedDataObjectId = ref<number>();
const selectedTraversalRules = ref<TraversalRules[]>([]);

const showCollectionInput = computed(() =>
  new Set<QueryContainerType>([
    QueryType.DataObject,
    QueryType.StructuredData,
    QueryType.Reference,
  ]).has(selectedQueryType.value),
);
const showDataObjectInput = computed(
  () => showCollectionInput.value && selectedCollectionId.value,
);
const showTraversalRulesInput = computed(
  () => showDataObjectInput.value && selectedDataObjectId.value,
);
const showAdditionalSearchCriteria = computed(() => showCollectionInput.value);
const searchDisabled = computed(
  () =>
    (selectedQueryType.value === QueryType.StructuredData &&
      !selectedCollectionId.value) ||
    !jsonQuery.value ||
    !isJsonQueryValid.value,
);

const loadingSearchResults = ref(false);
const searchResults = ref<SearchResult[]>([]);

const showJsonEditor = ref<boolean>(false);

function reset() {
  jsonQuery.value = initialJson;
  selectedCollectionId.value = undefined;
  selectedDataObjectId.value = undefined;
  selectedTraversalRules.value = [];
  selectedQueryType.value = QueryType.Collection;
  searchResults.value = [];
  removeAllQueryParams();
}

function onSearchButtonClicked() {
  if (!jsonQuery.value) throw new Error("No Json query provided!");
  setAllQueryParam();
  handleSearch();
}

function handleSearch() {
  if (!jsonQuery.value) throw new Error("No Json query provided!");
  if (!isJsonQueryValid.value) {
    throw new Error("Search query invalid!");
  }
  const scope: SearchScope = {
    collectionId: selectedCollectionId.value,
    dataObjectId: selectedDataObjectId.value,
    traversalRules: selectedTraversalRules.value,
  };

  let searchRequest = null;
  switch (selectedQueryType.value) {
    case QueryType.Collection:
      searchRequest = new SearchCollectionRequest(jsonQuery.value);
      break;
    case QueryType.DataObject:
      searchRequest = new SearchDataObjectRequest(jsonQuery.value, scope);
      break;
    case QueryType.Reference:
      searchRequest = new SearchReferenceRequest(jsonQuery.value, scope);
      break;
    case QueryType.StructuredData:
      searchRequest = new SearchStructuredRequest(jsonQuery.value, scope);
      break;
    case ContainerType.File:
      searchRequest = new SearchFileContainerRequest(jsonQuery.value);
      break;
    case ContainerType.Timeseries:
      searchRequest = new SearchTimeseriesContainerRequest(jsonQuery.value);
      break;
    case ContainerType.Structureddata:
      searchRequest = new SearchStructuredContainerRequest(jsonQuery.value);
      break;
    default:
      throw new Error("Query type not supported!");
  }

  search(searchRequest, loadingSearchResults, searchResults);
}

function setAllQueryParam() {
  setQueryParam("queryType", String(selectedQueryType.value));
  if (selectedCollectionId.value)
    setQueryParam("collectionId", String(selectedCollectionId.value));
  if (selectedDataObjectId.value)
    setQueryParam("dataObjectId", String(selectedDataObjectId.value));
  if (selectedTraversalRules.value.length > 0)
    setQueryParam(
      "traversalRules",
      JSON.stringify(selectedTraversalRules.value),
    );
  setQueryParam("searchQuery", JSON.stringify(JSON.parse(jsonQuery.value)));
}

function string2queryType(str: string): QueryContainerType {
  switch (str) {
    case "Collection":
      return QueryType.Collection;
    case "DataObject":
      return QueryType.DataObject;
    case "Reference":
      return QueryType.Reference;
    case "StructuredData":
      return QueryType.StructuredData;
    case "FILE":
      return ContainerType.File;
    case "STRUCTUREDDATA":
      return ContainerType.Structureddata;
    case "TIMESERIES":
      return ContainerType.Timeseries;
  }
  throw new Error(`Query type ${str} not supported!`);
}

const dataFromQueryParam = ref<boolean>(false);

function getAllQueryParam() {
  const params = useRequestURL().searchParams;

  const queryType = params.get("queryType");
  selectedQueryType.value = queryType
    ? string2queryType(queryType)
    : QueryType.Collection;

  const collectionId = params.get("collectionId");
  selectedCollectionId.value = collectionId ? Number(collectionId) : undefined;

  const dataObjectId = params.get("dataObjectId");
  selectedDataObjectId.value = dataObjectId ? Number(dataObjectId) : undefined;

  const traversalRules = params.get("traversalRules");
  selectedTraversalRules.value = traversalRules
    ? JSON.parse(traversalRules)
    : [];

  const searchQuery = params.get("searchQuery");
  jsonQuery.value = searchQuery ? searchQuery : initialJson;

  if (
    queryType ||
    collectionId ||
    dataObjectId ||
    traversalRules ||
    searchQuery
  ) {
    dataFromQueryParam.value = true;
    if (isJsonQueryValid.value) handleSearch();
  }
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

useTitle("Search | shepard");
getAllQueryParam();
</script>

<template>
  <div style="max-width: 1200px; margin: auto">
    <v-container fluid width="100%">
      <v-col>
        <v-row>
          <v-col class="py-14">
            <h1 class="text-h1 text-textbody1 pr-4">Advanced Search</h1>
          </v-col>
        </v-row>
        <v-row>
          <v-col cols="5">
            <v-row>
              <v-col><h2>Query</h2></v-col>
            </v-row>
            <v-row>
              <v-col>
                <QueryTypeInput v-model:query-type="selectedQueryType" />
              </v-col>
            </v-row>
            <v-row>
              <v-col>
                <SimpleInput
                  v-model:input-string="jsonQuery"
                  :error="!isJsonQueryValid"
                  :is-optional="false"
                  label="Search Query"
                  placeholder="Insert valid JSON Code"
                />
              </v-col>
            </v-row>
            <v-row>
              <v-col>
                <v-row>
                  <v-col style="display: inline-flex">
                    <v-switch
                      v-model:model-value="showJsonEditor"
                      color="primary"
                      density="compact"
                      hide-details
                      style="margin-right: 16px; margin-left: 8px"
                    />
                    <div style="display: flex; align-items: center">
                      Use Code view
                    </div>
                  </v-col>
                </v-row>
                <v-row>
                  <v-col>
                    <JsonEditor
                      v-if="showJsonEditor"
                      v-model:json="jsonQuery"
                    />
                  </v-col>
                </v-row>
              </v-col>
            </v-row>
            <v-row v-if="showAdditionalSearchCriteria">
              <v-col>
                <h2>Additional Search Criteria:</h2>
              </v-col>
            </v-row>
            <v-row v-if="showCollectionInput">
              <v-col>
                <CollectionInput
                  v-model:collection-id="selectedCollectionId"
                  :data-from-query-param="dataFromQueryParam"
                  :is-required="selectedQueryType === QueryType.StructuredData"
                  @selection-cleared="selectedCollectionId = undefined"
                />
              </v-col>
            </v-row>
            <v-row v-if="showDataObjectInput && selectedCollectionId">
              <v-col>
                <DataObjectInput
                  v-model:data-object-id="selectedDataObjectId"
                  :collection-id="selectedCollectionId"
                  :data-from-query-param="dataFromQueryParam"
                  @selection-cleared="selectedDataObjectId = undefined"
                />
              </v-col>
            </v-row>
            <v-row v-if="showTraversalRulesInput && selectedCollectionId">
              <v-col>
                <TraversalRuleInput
                  v-model:traversal-rules="selectedTraversalRules"
                  :collection-id="selectedCollectionId"
                />
              </v-col>
            </v-row>
            <v-row>
              <v-col>
                <v-row>
                  <v-col>
                    <v-btn
                      :disabled="searchDisabled"
                      class="float-right"
                      color="primary"
                      text="Search"
                      variant="flat"
                      @click="onSearchButtonClicked"
                    />
                    <v-btn
                      :entity-name="'Reset'"
                      class="mr-2 float-right"
                      color="treeview"
                      text="Reset"
                      variant="flat"
                      @click="reset"
                    />
                  </v-col>
                </v-row>
              </v-col>
            </v-row>
          </v-col>
          <v-col />
          <v-col cols="6">
            <v-row>
              <v-col>
                <h2>Results</h2>
              </v-col>
            </v-row>
            <v-row>
              <v-col>
                <SearchResultList
                  :loading="loadingSearchResults"
                  :search-results="searchResults"
                />
              </v-col>
            </v-row>
          </v-col>
        </v-row>
      </v-col>
    </v-container>
  </div>
</template>

<style lang="scss" scoped>
h2 {
  font-weight: 500;
}
</style>
