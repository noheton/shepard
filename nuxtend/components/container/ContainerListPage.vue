<script setup lang="ts">
import ContainerTypeSelect from "./ContainerTypeSelect.vue";
import { useSearchContainers } from "./useSearchContainers";

const itemsPerPage = 20;

const { serverItems, pageCount, loading, searchResultHint } =
  useSearchContainers(itemsPerPage);
</script>

<template>
  <div style="max-width: 1200px; margin: auto">
    <v-container fluid>
      <v-row>
        <v-col cols="12" class="py-14">
          <div class="d-flex align-baseline">
            <h1 class="text-h1 text-textbody1 pr-4">Containers</h1>
            <Tooltip>
              <div>
                The data you reference in your project collections is stored in
                containers.
              </div>
              <div>
                There are different types of containers for the different types
                of data they store.
              </div>
            </Tooltip>
          </div>
        </v-col>
        <v-col cols="12" class="pb-4">
          <ContainerSearchField :search-result-hint="searchResultHint" />
        </v-col>
        <v-col cols="12" class="pt-4 pb-1">
          <ContainerTypeSelect />
        </v-col>
        <v-col cols="12">
          <ContainerList
            :items-per-page="itemsPerPage"
            :server-items="serverItems"
            :loading="loading"
            :page-count="pageCount"
          />
        </v-col>
      </v-row>
    </v-container>
  </div>
</template>
