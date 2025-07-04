<script lang="ts" setup>
import type { TraversalRules } from "@dlr-shepard/backend-client";
import { QueryType } from "@dlr-shepard/backend-client";
import { useTitle } from "@vueuse/core";
import { useCollectionAdvancedSearch } from "~/components/context/Search/context/useCollectionAdvancedSearch";
import { useContainerAdvancedSearch } from "~/components/context/Search/context/useContainerAdvancedSerach";
import { useDataObjectAdvancedSearch } from "~/components/context/Search/context/useDataObjectAdvancedSearch";
import { useReferenceAdvancedSearch } from "~/components/context/Search/context/useReferenceAdvancedSearch";
import { useStructuredDataAdvancedSearch } from "~/components/context/Search/context/useStructuredDataAdvancedSearch";

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
const selectedQueryType = ref<string>("");
const currentCollectionId = ref<number>();
const currentDataObjectId = ref<number>();
// const traversalRuleOptions = Object.values(TraversalRules);
// const traversalRulesDisabled = computed(() => {

const queryType = ref<QueryType>(QueryType.Collection);
const selectedTraversalRules = ref<TraversalRules[]>([]);
const collectionId = ref<number | undefined>(undefined);
const dataObjectId = ref<number | undefined>(undefined);

const searchParam = ref<{
  selectedQueryType: string;
  searchQuery?: string;
  collectionId?: number;
  dataObjectId?: number;
  traversalRules?: TraversalRules[];
}>({ selectedQueryType: "" });

const collectionResults = useCollectionAdvancedSearch(searchParam);
const dataObjectResults = useDataObjectAdvancedSearch(searchParam);
const referenceResults = useReferenceAdvancedSearch(searchParam);
const containerResults = useContainerAdvancedSearch(searchParam);
const sdResults = useStructuredDataAdvancedSearch(searchParam);

const loading = computed(
  () =>
    collectionResults.loading.value ||
    dataObjectResults.loading.value ||
    referenceResults.loading.value ||
    sdResults.loading.value ||
    containerResults.loading.value,
);

/*
const resultsTableFields = [
  { key: "id", label: "ID", sortable: true },
  { key: "name", label: "Name", sortable: true },
];

const results = computed(() =>
  [
    collectionResults.results.value,
    dataObjectResults.results.value,
    referenceResults.results.value,
    sdResults.results.value,
    containerResults.results.value,
  ].find(x => x.length > 0),
);
*/

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
  <div style="max-width: 1200px; margin: auto">
    <v-container fluid width="100%">
      <v-row>
        <v-col class="py-14" cols="12">
          <div class="d-flex align-baseline">
            <h1 class="text-h1 text-textbody1 pr-4">Advanced Search</h1>
          </div>
        </v-col>
      </v-row>
      <v-row>
        <v-col class="font-weight-bold">Query</v-col>
      </v-row>
      <v-row>
        <v-col>
          <QueryTypeInput v-model:query-type="queryType" />
        </v-col>
      </v-row>
      <v-row>
        <v-col class="font-weight-bold">Additional Search Criteria:</v-col>
      </v-row>
      <v-row>
        <v-col cols="12">
          <v-row>
            <v-col class="py-14" cols="6">
              <div class="d-flex align-baseline">
                <h1 class="text-h1 text-textbody1 pr-4">Advanced Search</h1>
              </div>
            </v-col>
          </v-row>
          <v-row>
            <v-col class="font-weight-bold">Query</v-col>
          </v-row>
          <v-row>
            <v-col>
              <QueryTypeInput v-model:query-type="queryType" />
            </v-col>
          </v-row>
          <v-row>
            <v-col class="font-weight-bold">Additional Search Criteria:</v-col>
          </v-row>
          <v-row>
            <v-col cols="12">
              <CollectionInput
                v-model:collection-id="collectionId"
                @selection-cleared="collectionId = undefined"
              />
            </v-col>
          </v-row>
          <v-row v-if="collectionId">
            <v-col cols="12">
              <DataObjectInput
                v-model:data-object-id="dataObjectId"
                :collection-id="collectionId"
                @selection-cleared="dataObjectId = undefined"
              />
            </v-col>
          </v-row>
          <v-row v-if="collectionId && dataObjectId">
            <v-col cols="12">
              <TraversalRuleInput
                v-model:traversal-rule="selectedTraversalRules"
                :collection-id="collectionId"
              />
            </v-col>
          </v-row>
          <v-row>
            <v-col cols="4">
              <ResetSearchButton
                :entity-name="'Search'"
                class="mr-2 float-right"
                @click="handleSearch()"
              />
              <ResetSearchButton
                :entity-name="'Reset'"
                class="mr-2 float-right"
                @click="reset()"
              />
            </v-col>
          </v-row>
        </v-col>

        <v-col cols="12">
          <CenteredLoadingSpinner v-if="loading" />
          test
        </v-col>
      </v-row>
      <v-row>
        <CustomJsonEditor v-model:search-query="jsonQuery" />
      </v-row>
    </v-container>
  </div>
</template>
