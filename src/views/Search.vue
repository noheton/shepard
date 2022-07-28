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
                    v-model="selectedTraversalRule"
                    :disabled="
                      selectedQueryType == 'Collection' ||
                      [undefined, ''].includes(currentDataObjectId)
                    "
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
                    <b-button variant="dark" @click="reset()"> Reset </b-button>
                    <b-button variant="primary" @click="query()">
                      Search
                    </b-button>
                  </b-button-group>
                </div>
              </b-col>
            </b-row>
          </b-col>

          <b-col>
            <div table>
              <div class="pl-2 resultHeader">Result</div>

              <div v-if="searchData != undefined">
                <b-table
                  v-if="searchData.resultSet?.length > 0"
                  sticky-header="766px"
                  striped
                  hover
                  selectable
                  select-mode="single"
                  head-variant="dark"
                  class="table table-sm table-bordered"
                  :items="searchData.resultSet"
                  @row-selected="rowSelected"
                >
                </b-table>
                <div v-else class="pl-2">no results</div>
              </div>
              <Loading v-if="loading" />
            </div>
          </b-col>
        </b-row>
      </b-container>
    </div>
  </div>
</template>

<script lang="ts">
import Loading from "@/components/generic/Loading.vue";
import SearchService from "@/services/searchService";
import { emitter } from "@/utils/event-bus";
import {
  SearchParamsQueryTypeEnum,
  SearchScopeTraversalRulesEnum,
  type ResponseBody,
  type ResultTriple,
} from "@dlr-shepard/shepard-client";
import JSONEditor, { type JSONEditorOptions } from "jsoneditor";
import "jsoneditor/dist/jsoneditor.css";
import { defineComponent } from "vue";

interface SearchData {
  editor?: JSONEditor;
  currentCollectionId?: number;
  currentDataObjectId?: number;
  traversalRuleOptions: object;
  queryTypeOptions: object;
  selectedTraversalRule: Array<SearchScopeTraversalRulesEnum>;
  selectedQueryType: SearchParamsQueryTypeEnum;
  searchData?: ResponseBody;
  loading: boolean;
}

function initialState(): SearchData {
  return {
    editor: undefined,
    currentCollectionId: undefined,
    currentDataObjectId: undefined,
    selectedTraversalRule: [],
    selectedQueryType: SearchParamsQueryTypeEnum.Collection,
    traversalRuleOptions: Object.values(SearchScopeTraversalRulesEnum),
    queryTypeOptions: Object.values(SearchParamsQueryTypeEnum),
    searchData: undefined,
    loading: false,
  };
}

const initialJson = {
  OR: [
    {
      property: "name",
      value: "MyName",
      operator: "eq",
    },
    {
      NOT: {
        property: "id",
        value: 12,
        operator: "gt",
      },
    },
  ],
};

export default defineComponent({
  components: { Loading },
  data() {
    return initialState();
  },
  mounted() {
    this.jsonEditor();
  },
  methods: {
    jsonEditor() {
      const options = {
        mode: "tree",
        modes: ["code", "tree"],
        search: false,
      } as JSONEditorOptions;

      // create the editor
      const container = document.getElementById("jsoneditor");
      if (container) {
        this.editor = new JSONEditor(container, options);
      } else {
        this.editor = undefined;
      }

      if (this.editor) {
        this.editor.set(initialJson);
      }
    },

    reset() {
      const editor = this.editor;
      Object.assign(this.$data, initialState());
      this.editor = editor;
      if (this.editor) {
        this.editor.set(initialJson);
      }
    },

    query() {
      // get actual json of the JsonEditor
      if (!this.editor) return;
      const searchQuery = JSON.stringify(this.editor.get());
      this.searchData = undefined;
      this.loading = true;

      SearchService.search({
        searchBody: {
          scopes: [
            {
              collectionId: this.currentCollectionId,
              dataObjectId: this.currentDataObjectId,
              traversalRules: this.selectedTraversalRule,
            },
          ],
          searchParams: {
            query: searchQuery,
            queryType: this.selectedQueryType,
          },
        },
      })
        .then(response => {
          this.searchData = response;
        })
        .catch(e => {
          const error = "Error while fetching search Data: " + e.statusText;
          console.log(error);
          emitter.emit("error", error);
        })
        .finally(() => {
          this.loading = false;
        });
    },

    rowSelected(items: Array<ResultTriple>) {
      if (items.length == 0) return;

      const item = items[0];
      let routeData = undefined;

      if (item.dataObjectId != undefined) {
        routeData = this.$router.resolve({
          name: "DataObject",
          params: {
            collectionId: String(item.collectionId),
            dataObjectId: String(item.dataObjectId),
          },
        });
      } else {
        routeData = this.$router.resolve({
          name: "Collection",
          params: {
            collectionId: String(item.collectionId),
          },
        });
      }
      window.open(routeData.href, "_blank");
    },
  },
});
</script>

<style type="text/css">
#jsoneditor {
  border-color: dark;
  max-height: 500px;
  height: 500px;
}

.jsoneditor {
  border: thin solid #343a40;
}

.jsoneditor-menu {
  background-color: #343a40;
  border-bottom: 1px solid #343a40;
}

.resultHeader {
  background-color: #343a40;
  color: white;
  font-size: 1.5em;
  font-weight: bold;
  border: 1px solid;
}
</style>
