<script lang="ts" setup>
import ContainerTypeSelect from "./ContainerTypeSelect.vue";
import { useSearchContainers } from "./useSearchContainers";
import type { ContainerType } from "@dlr-shepard/backend-client";

const itemsPerPage = 20;

const { serverItems, pageCount, loading, searchResultHint } =
  useSearchContainers(itemsPerPage);

const showCreateDialog = ref(false);

const isEmpty = computed(
  () => !loading.value && serverItems.value.length === 0 && !searchResultHint.value,
);
</script>

<template>
  <div style="max-width: 1200px; margin: auto">
    <v-container fluid>
      <v-row>
        <v-col class="py-14" cols="12">
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

        <template v-if="isEmpty">
          <v-col cols="12" class="d-flex flex-column align-center py-16">
            <v-icon icon="mdi-database-outline" size="72" color="textbody2" class="mb-4" />
            <div class="text-h4 text-semibold mb-2">No containers yet</div>
            <div class="text-body-1 text-textbody2 mb-6">
              Create your first container to start storing your raw data.
            </div>
            <v-btn
              class="bg-primary text-canvas"
              variant="flat"
              size="large"
              @click="showCreateDialog = true"
            >
              <template #prepend>
                <v-icon color="canvas" icon="mdi-plus-circle" />
              </template>
              Create container
            </v-btn>
          </v-col>
        </template>

        <template v-else>
          <v-col class="pb-4" cols="auto">
            <ContainerSearchField :search-result-hint="searchResultHint" />
          </v-col>
          <v-spacer />
          <v-col class="pb-4" cols="auto" justify-self="end">
            <v-btn
              :style="{ marginTop: '3px' }"
              class="bg-primary text-canvas"
              variant="flat"
              @click="showCreateDialog = true"
            >
              <template #prepend>
                <v-icon color="canvas" icon="mdi-plus-circle" />
              </template>
              Create new container
            </v-btn>
          </v-col>
          <v-col class="pt-4 pb-1" cols="12">
            <ContainerTypeSelect />
          </v-col>
          <v-col cols="12">
            <ContainerList
              :items-per-page="itemsPerPage"
              :loading="loading"
              :page-count="pageCount"
              :server-items="serverItems"
            />
          </v-col>
        </template>
      </v-row>
    </v-container>
    <CreateContainerDialog
      v-if="showCreateDialog"
      v-model:show-dialog="showCreateDialog"
      @container-created="
        (id: number, type: ContainerType) =>
          $router.push(
            containersPath + containerTypeUrlPathSegmentMappings[type] + id,
          )
      "
    />
  </div>
</template>
