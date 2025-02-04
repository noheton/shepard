<script setup lang="ts">
import ContainerTypeSelect from "./ContainerTypeSelect.vue";
import { useSearchContainers } from "./useSearchContainers";

const itemsPerPage = 20;

const { serverItems, pageCount, loading } = useSearchContainers(itemsPerPage);
</script>

<template>
  <div style="max-width: 1200px; margin: auto">
    <v-container fluid>
      <v-row no-gutters>
        <v-col cols="12" no-gutters>
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
      </v-row>
      <v-row no-gutters>
        <v-col cols="6" class="pt-14">
          <ContainerSearchField />
        </v-col>
      </v-row>
      <v-row no-gutters>
        <v-col cols="4" no-gutters class="pt-8">
          <ContainerTypeSelect />
        </v-col>
      </v-row>
      <v-row no-gutters>
        <v-col cols="12" no-gutters class="pt-4">
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

<style lang="scss" scoped>
:deep(.v-field--variant-outlined.v-field--focused .v-field__outline) {
  --v-field-border-width: 1px;
}
</style>
