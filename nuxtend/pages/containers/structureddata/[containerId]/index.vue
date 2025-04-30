<script setup lang="ts">
import type { StructuredData } from "@dlr-shepard/backend-client";
import { StructuredDataContainerAccessor } from "~/composables/container/StructuredDataAccessor";

const { routeParams } = useContainerRouteParams();
const containerId = routeParams.value.containerId;
const urlSegment = containerTypeUrlPathSegmentMappings.STRUCTUREDDATA;

const container = new StructuredDataContainerAccessor(containerId);
container.fetchData();
container.fetchItems();
container.fetchRoles();
container.fetchPermissions();

onContainerUpdated(() => {
  container.fetchData();
  container.fetchItems();
});

const itemToDelete = ref<StructuredData | undefined>(undefined);
const showFileDeleteConfirmDialog = ref<boolean>(false);

const deleteItem = (item: StructuredData) => {
  itemToDelete.value = item;
  showFileDeleteConfirmDialog.value = true;
};
</script>

<template>
  <div style="max-width: 1200px; margin: auto">
    <v-container fluid>
      <v-row v-if="!!container.container.value" no-gutters>
        <v-col cols="12">
          <Breadcrumbs
            :items="[
              {
                title: 'Containers',
                to: containersPath,
              },
              {
                title: container.container.value.name,
                to: containersPath + urlSegment + containerId,
              },
            ]"
          />
        </v-col>
        <v-col cols="12">
          <v-container class="pa-0" fluid>
            <v-row no-gutters>
              <ContainerTitleAndMetadataDisplay
                :id="container.container.value.id"
                :n-items="container.items.value.length"
                :name="container.container.value.name"
                :type-label="'Structured Data Container'"
              >
                <template #buttons>
                  <!-- <v-btn
                    v-if="container.isAllowedToEditData.value"
                    rounded="lg"
                    variant="flat"
                    color="primary"
                    prepend-icon="mdi-plus-circle"
                  >
                    Add File
                  </v-btn> -->
                  <EditPermissionsButton
                    v-if="container.isAllowedToEditPermissions.value"
                    :shepard-object-accessor="container"
                  />
                  <DeleteContainerButton
                    v-if="container.isAllowedToDelete.value"
                    :entity-name="container.container.value.name"
                    @delete="container.delete()"
                  />
                </template>
              </ContainerTitleAndMetadataDisplay>
            </v-row>
          </v-container>
        </v-col>
      </v-row>
      <CenteredLoadingSpinner v-else />
      <StructuredDataTable
        :is-allowed-to-edit="container.isAllowedToEditData.value"
        :items="container.items.value"
        :loading="container.loading.value"
        @delete-item="item => deleteItem(item)"
      />
    </v-container>
    <ConfirmDeleteDialog
      v-if="
        showFileDeleteConfirmDialog && itemToDelete?.oid && itemToDelete.name
      "
      v-model:show-dialog="showFileDeleteConfirmDialog"
      @confirmed="container.deleteItem(itemToDelete.oid)"
    />
  </div>
</template>
