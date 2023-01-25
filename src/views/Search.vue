<script setup lang="ts">
import Loading from "@/components/generic/Loading.vue";
import SearchService from "@/services/searchService";
import { handleError } from "@/utils/error-handling";
import {
  ResponseError,
  SearchParamsQueryTypeEnum,
  SearchScopeTraversalRulesEnum,
  type ResponseBody,
  type ResultTriple,
} from "@dlr-shepard/shepard-client";
import { useTitle } from "@vueuse/core";
import { default as JSONEditor, type JSONEditorOptions } from "jsoneditor";
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue2-helpers/vue-router";

const initialJson = {
  OR: [
    {
      property: "name",
      operator: "eq",
      value: "MyName",
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

const router = useRouter();
const traversalRuleOptions = Object.values(SearchScopeTraversalRulesEnum);
const queryTypeOptions = Object.values(SearchParamsQueryTypeEnum);

const currentCollectionId = ref<number>();
const currentDataObjectId = ref<number>();
const selectedTraversalRules = ref<SearchScopeTraversalRulesEnum[]>([]);
const selectedQueryType = ref<SearchParamsQueryTypeEnum>("Collection");

const traversalRulesDisabled = computed(() => {
  return (
    selectedQueryType.value == "Collection" ||
    currentDataObjectId.value == undefined ||
    currentDataObjectId.value.toString() == ""
  );
});

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
    editor.value.set(initialJson);
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
  searchData.value = undefined;
  loading.value = false;
  maxResultsReached.value = false;
}

const maxResults = 1000;
const searchData = ref<ResponseBody>();
const loading = ref(false);
const maxResultsReached = ref(false);
function query() {
  // get actual json of the JsonEditor
  if (!editor.value) return;
  const searchQuery = JSON.stringify(editor.value.get());
  searchData.value = undefined;
  loading.value = true;

  SearchService.search({
    searchBody: {
      scopes: [
        {
          collectionId: currentCollectionId.value,
          dataObjectId: currentDataObjectId.value,
          traversalRules: selectedTraversalRules.value,
        },
      ],
      searchParams: {
        query: searchQuery,
        queryType: selectedQueryType.value,
      },
    },
  })
    .then(response => {
      if (response.resultSet && response.resultSet.length > maxResults) {
        response.resultSet = response.resultSet.slice(0, maxResults);
        maxResultsReached.value = true;
      } else {
        maxResultsReached.value = false;
      }
      searchData.value = response;
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching search data");
    })
    .finally(() => {
      loading.value = false;
    });
}

function rowSelected(items: ResultTriple[]) {
  if (items.length == 0) return;

  const item = items[0];
  let routeData = undefined;

  if (item.dataObjectId != undefined) {
    routeData = router.resolve({
      name: "DataObject",
      params: {
        collectionId: String(item.collectionId),
        dataObjectId: String(item.dataObjectId),
      },
    });
  } else {
    routeData = router.resolve({
      name: "Collection",
      params: {
        collectionId: String(item.collectionId),
      },
    });
  }
  window.open(routeData.href, "_blank");
}

onMounted(() => {
  jsonEditor();
  useTitle("Search | shepard");
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
                <b-form-select
                  v-model="selectedQueryType"
                  :options="queryTypeOptions"
                >
                </b-form-select>
              </b-col>
            </b-row>

            <b-row class="mb-2">
              <b-col cols="4"> Collection ID </b-col>
              <b-col cols="8">
                <b-form-input
                  v-model="currentCollectionId"
                  :disabled="selectedQueryType == 'Collection'"
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
                  :disabled="selectedQueryType == 'Collection'"
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
                    <b-button variant="primary" @click="query()">
                      Search
                    </b-button>
                  </b-button-group>
                </div>
              </b-col>
            </b-row>
          </b-col>

          <b-col>
            <div class="pl-2 result-header">Result</div>
            <b-alert :show="maxResultsReached" variant="warning">
              Maximum number of results reached. <br />
              Only {{ maxResults }} elements are displayed.
            </b-alert>
            <div v-if="searchData != undefined">
              <b-table
                v-if="searchData.resultSet && searchData.resultSet.length > 0"
                sticky-header="766px"
                head-variant="light"
                striped
                hover
                selectable
                select-mode="single"
                class="table table-sm table-bordered"
                :items="searchData.resultSet"
                @row-selected="rowSelected"
              >
              </b-table>
              <div v-else class="pl-2">no results</div>
            </div>
            <Loading v-if="loading" />
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
