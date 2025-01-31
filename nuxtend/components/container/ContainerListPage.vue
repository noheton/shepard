<script setup lang="ts">
import ContainerTypeSelect from "./ContainerTypeSelect.vue";

const itemsPerPage = 20;

const { queryParams } = useContainerListRouteParams();

const { serverItems, pageCount, loading } = useSearchContainers(
  queryParams,
  itemsPerPage,
);
</script>

<template>
  <div style="max-width: 1200px; margin: auto">
    <v-container fluid>
      <v-row no-gutters>
        <v-col cols="12" no-gutters>
          <CommonTooltip>
            <h1 class="text-h1 pr-4">Containers</h1>
            <template #content>
              <div>
                The data you reference in your project collections is stored in
                containers.
              </div>
              <div>
                There are different types of containers for the different types
                of data they store.
              </div>
            </template>
          </CommonTooltip>
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
