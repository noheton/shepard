<script lang="ts" setup>
import {
  ContainerType,
  QueryType,
  type SearchScope,
  type TraversalRules,
} from "@dlr-shepard/backend-client";
import type { SearchResult } from "~/components/context/search/context/searchResultTypes";
import {
  search,
  SearchCollectionRequest,
  SearchDataObjectRequest,
  SearchFileContainerRequest,
  SearchReferenceRequest,
  SearchStructuredContainerRequest,
  SearchStructuredRequest,
  SearchTimeseriesContainerRequest,
} from "~/components/context/search/context/searchService";
import CollectionPrefillableInput from "~/components/context/search/input-components/CollectionPrefillableInput.vue";
import DataObjectPrefillableInput from "~/components/context/search/input-components/DataObjectPrefillableInput.vue";
import QueryBuilder from "~/components/context/search/input-components/QueryBuilder.vue";
import type { QueryContainerType } from "~/components/context/search/input-components/QueryTypeInput.vue";
import { clearQueryParams } from "~/utils/helpers";
import {
  buildNameContainsQuery,
  searchQueryFromParams,
} from "~/utils/searchQueryFromParams";

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

// UI-SEARCH-JSON-QUERY-001 — the simple `q=` search the casual researcher
// expects. Substring match on `name`. Power users open the Advanced panel
// (collapsed by default) for property+operator+value rows or raw JSON.
const simpleQ = ref<string>("");
// `advancedOpen` is a v-expansion-panel model-value (array of opened panel
// indices). Empty array = collapsed.
const advancedOpen = ref<number[]>([]);

function runSimpleSearch() {
  const needle = simpleQ.value.trim();
  if (needle === "") return;
  selectedQueryType.value = QueryType.Collection;
  selectedCollectionId.value = undefined;
  selectedDataObjectId.value = undefined;
  selectedTraversalRules.value = [];
  jsonQuery.value = buildNameContainsQuery(needle);
  setQueryParam("q", needle);
  removeQueryParam("searchQuery");
  removeQueryParam("queryType");
  handleSearch();
}

watch(selectedCollectionId, () => {
  selectedDataObjectId.value = undefined;
});

function reset() {
  jsonQuery.value = initialJson;
  selectedCollectionId.value = undefined;
  selectedDataObjectId.value = undefined;
  selectedTraversalRules.value = [];
  selectedQueryType.value = QueryType.Collection;
  searchResults.value = [];
  simpleQ.value = "";
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
  clearQueryParams();
  setQueryParam("queryType", String(selectedQueryType.value));
  if (selectedCollectionId.value)
    setQueryParam("collectionId", String(selectedCollectionId.value));
  if (selectedDataObjectId.value)
    setQueryParam("dataObjectId", String(selectedDataObjectId.value));
  if (showTraversalRulesInput.value)
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

  // UX bonus (2026-05-24): support `?q=<text>` as the simple free-text param
  // the header-search dropdown's "See all results" link uses.
  // `searchQuery` (structured JSON form) wins when both are present.
  const { jsonQuery: derivedJson, shouldRun } = searchQueryFromParams(
    params,
    initialJson,
  );
  jsonQuery.value = derivedJson;

  // Mirror ?q= into the simple-mode input so the user can edit it. When
  // `searchQuery` is also present it wins (Advanced), so the simple field
  // stays empty in that case.
  const rawQ = params.get("q");
  if (rawQ && rawQ.trim() !== "" && !params.get("searchQuery")) {
    simpleQ.value = rawQ.trim();
  }

  if (
    queryType ||
    collectionId ||
    dataObjectId ||
    traversalRules ||
    shouldRun
  ) {
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
    "q",
  ].forEach(qp => removeQueryParam(qp));
}

getAllQueryParam();

useHead({
  title: "Search | shepard",
});
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
            <!-- UI-SEARCH-JSON-QUERY-001 — primary path: free-text search.
                 Casual users never need to open the Advanced panel. -->
            <v-row>
              <v-col>
                <v-text-field
                  v-model="simpleQ"
                  label="Search collections by name"
                  placeholder="e.g. hotfire, TR-004, mffd"
                  prepend-inner-icon="mdi-magnify"
                  variant="outlined"
                  density="comfortable"
                  clearable
                  hide-details
                  autofocus
                  @keyup.enter="runSimpleSearch"
                />
              </v-col>
              <v-col cols="auto" class="d-flex align-center">
                <v-btn
                  color="primary"
                  variant="flat"
                  :disabled="!simpleQ.trim()"
                  @click="runSimpleSearch"
                >
                  Search
                </v-btn>
              </v-col>
            </v-row>
            <v-row>
              <v-col>
                <v-expansion-panels v-model="advancedOpen" variant="accordion">
                  <v-expansion-panel>
                    <v-expansion-panel-title>
                      <v-icon start size="small">mdi-tune</v-icon>
                      Advanced query
                    </v-expansion-panel-title>
                    <v-expansion-panel-text>
                      <v-row>
                        <v-col>
                          <h3 class="text-subtitle-1">Query type</h3>
                        </v-col>
                      </v-row>
                      <v-row>
                        <v-col>
                          <QueryTypeInput
                            v-model:query-type="selectedQueryType"
                          />
                        </v-col>
                      </v-row>
                      <v-row>
                        <v-col>
                          <h3 class="text-subtitle-1">Filters</h3>
                          <p class="text-caption text-medium-emphasis">
                            Combine N filters with AND or OR. JSON preview
                            shows the wire shape POSTed to the backend.
                          </p>
                          <QueryBuilder v-model:json-query="jsonQuery" />
                        </v-col>
                      </v-row>
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
                            Raw JSON (power-user override)
                          </div>
                        </v-col>
                      </v-row>
                      <v-row v-if="showJsonEditor">
                        <v-col>
                          <SimpleInput
                            v-model:input-string="jsonQuery"
                            :error="!isJsonQueryValid"
                            :is-optional="false"
                            label="Raw JSON query"
                            placeholder="Insert valid JSON Code"
                          />
                          <JsonEditor v-model:json="jsonQuery" />
                        </v-col>
                      </v-row>
                      <v-row v-if="showAdditionalSearchCriteria">
                        <v-col>
                          <h3 class="text-subtitle-1">
                            Additional search criteria
                          </h3>
                        </v-col>
                      </v-row>
                      <v-row v-if="showCollectionInput">
                        <v-col>
                          <CollectionPrefillableInput
                            v-model:collection-id="selectedCollectionId"
                            :is-required="
                              selectedQueryType === QueryType.StructuredData
                            "
                          />
                        </v-col>
                      </v-row>
                      <v-row v-if="showDataObjectInput && selectedCollectionId">
                        <v-col>
                          <DataObjectPrefillableInput
                            v-model:data-object-id="selectedDataObjectId"
                            :collection-id="selectedCollectionId"
                          />
                        </v-col>
                      </v-row>
                      <v-row
                        v-if="showTraversalRulesInput && selectedCollectionId"
                      >
                        <v-col>
                          <TraversalRuleInput
                            v-model:traversal-rules="selectedTraversalRules"
                            :collection-id="selectedCollectionId"
                          />
                        </v-col>
                      </v-row>
                      <v-row>
                        <v-col>
                          <v-btn
                            :disabled="searchDisabled"
                            class="float-right"
                            color="primary"
                            text="Run advanced search"
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
                    </v-expansion-panel-text>
                  </v-expansion-panel>
                </v-expansion-panels>
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
