<script setup lang="ts">
import type { TraversalRules } from "@dlr-shepard/backend-client";
import { QueryType } from "@dlr-shepard/backend-client";

/*
const selectedQueryType = ref<string>("");
const searchParam = ref<{
  selectedQueryType: string;
  searchQuery?: string;
  collectionId?: number;
  dataObjectId?: number;
  traversalRules?: TraversalRules[];
}>({ selectedQueryType: "" });
*/

const queryType = ref<QueryType>(QueryType.Collection);
const selectedTraversalRules = ref<TraversalRules[]>([]);
const collectionId = ref<number | undefined>(undefined);
const dataObjectId = ref<number | undefined>(undefined);
</script>

<template>
  <div style="max-width: 1200px; margin: auto">
    <v-container width="100%" fluid>
      <v-row>
        <v-col cols="12" class="py-14">
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
    </v-container>
  </div>
</template>
