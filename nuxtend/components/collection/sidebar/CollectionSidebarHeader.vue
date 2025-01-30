<script lang="ts" setup>
import type { Collection } from "@dlr-shepard/backend-client";
import CommonInputDescription from "~/components/common/input/CommonInputDescription.vue";
import CommonInputName from "~/components/common/input/CommonInputName.vue";

interface CollectionSidebarHeaderProps {
  isFocused: boolean;
  height?: string;
  collection?: Collection;
  isAllowedToEditCollection?: boolean;
  isAllowedToEditPermissions?: boolean;
}
const props = defineProps<CollectionSidebarHeaderProps>();

const showContextMenuButton = ref<boolean>(false);

const showEditDialog = ref(false);
const dialogTitle = `Edit "${props.collection?.name}"`;
</script>

<template>
  <v-card
    flat
    hover
    rounded="0"
    :height="props.height"
    :color="
      props.isFocused
        ? 'rgb(var(--v-theme-focus1))'
        : 'rgb(var(--v-theme-treeview))'
    "
    :to="collectionsPath + `${collection?.id}`"
    :style="{
      borderLeft: props.isFocused
        ? '5px solid rgb(var(--v-theme-primary))'
        : '5px solid rgb(var(--v-theme-treeview))',
    }"
    class="d-flex ga-0 mb-4"
  >
    <v-card-item class="w-100">
      <template v-if="!!collection">
        <div
          class="ml-1 text-h4"
          style="
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            display: flex;
            justify-content: space-between;
            align-items: baseline;
          "
        >
          {{ collection.name }}
          <template
            v-if="
              isAllowedToEditCollection &&
              isAllowedToEditPermissions !== undefined
            "
          >
            <CollectionSidebarDisplayChildrenOnHover
              :display-children-without-hover="
                showContextMenuButton || showEditDialog
              "
            >
              <CommonContextMenu
                :items="[
                  {
                    label: 'Edit',
                    icon: 'mdi-pencil-outline',
                    onClick: () => (showEditDialog = true),
                  },
                ]"
                @expansion-state-changed="e => (showContextMenuButton = e)"
              />
            </CollectionSidebarDisplayChildrenOnHover>
            <CollectionEditDialog
              v-if="showEditDialog"
              v-model:show-dialog="showEditDialog"
              v-model:show-context-menu-button="showContextMenuButton"
              :collection="collection"
              :is-allowed-to-edit-permissions="isAllowedToEditPermissions"
              :title="dialogTitle"
            >
              <template
                #inputs="{
                  collectionId,
                  updateCollection,
                  updatePermissions,
                  updatedCollection,
                  updatedPermissions,
                }"
              >
                <CommonInputName
                  :name="updatedCollection.name"
                  @name-changed="
                    name => updateCollection({ ...updatedCollection, name })
                  "
                />
                <CommonInputDescription
                  :description="updatedCollection.description"
                  @description-changed="
                    description =>
                      updateCollection({ ...updatedCollection, description })
                  "
                />
                <CollectionEditPermissionsInput
                  v-if="isAllowedToEditPermissions"
                  :updated-permissions="updatedPermissions"
                  :collection-id="collectionId"
                  :update-permissions="updatePermissions"
                />
                <v-row>
                  <div class="text-body-3 text-textbody2">
                    *mandatory fields
                  </div>
                </v-row>
              </template>
            </CollectionEditDialog>
          </template>
        </div>
      </template>
    </v-card-item>
  </v-card>
</template>
