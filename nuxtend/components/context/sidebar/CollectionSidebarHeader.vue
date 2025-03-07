<script lang="ts" setup>
import type { Collection } from "@dlr-shepard/backend-client";

interface CollectionSidebarHeaderProps {
  isFocused: boolean;
  collection?: Collection;
  isAllowedToEditCollection?: boolean;
  isAllowedToEditPermissions?: boolean;
}
defineProps<CollectionSidebarHeaderProps>();

const showContextMenuButton = ref<boolean>(false);

const showEditDialog = ref(false);
const showDeleteDialog = ref(false);
</script>

<template>
  <div
    class="ml-6 text-textbody1 text-overline"
    style="margin-top: 40px; margin-bottom: 4px"
  >
    Collection
  </div>
  <v-card
    flat
    hover
    rounded="0"
    style="min-height: 40px; max-height: 40px"
    :to="collectionsPath + `${collection?.id}`"
    :class="`d-flex ${isFocused ? 'sidebar-item-focused' : 'sidebar-item'}`"
  >
    <v-card-item class="w-100">
      <template v-if="!!collection">
        <div
          class="ml-1 text-h4"
          style="
            display: flex;
            flex-wrap: nowrap;
            justify-content: space-between;
            align-items: stretch;
          "
        >
          <div
            style="
              min-width: 0;
              flex: 1;
              overflow: hidden;
              text-overflow: ellipsis;
              white-space: nowrap;
            "
          >
            {{ collection.name }}
          </div>

          <div
            v-if="
              isAllowedToEditCollection &&
              isAllowedToEditPermissions !== undefined
            "
            style="flex-shrink: 0; margin-left: 10px"
          >
            <DisplayChildrenOnHover
              :display-children-without-hover="
                showContextMenuButton || showEditDialog
              "
            >
              <ContextMenu
                :items="[
                  {
                    label: 'Edit',
                    icon: 'mdi-pencil-outline',
                    onClick: () => (showEditDialog = true),
                  },
                  {
                    label: 'Delete',
                    icon: 'mdi-delete-outline',
                    onClick: () => (showDeleteDialog = true),
                  },
                ]"
                @expansion-state-changed="e => (showContextMenuButton = e)"
              />
            </DisplayChildrenOnHover>
            <EditCollectionDialog
              v-if="showEditDialog"
              v-model:show-dialog="showEditDialog"
              :collection="collection"
              :is-allowed-to-edit-permissions="isAllowedToEditPermissions"
            />
            <DeleteCollectionDialog
              v-if="showDeleteDialog"
              v-model:show-dialog="showDeleteDialog"
              :collection="collection"
            />
          </div>
        </div>
      </template>
    </v-card-item>
  </v-card>
</template>

<style lang="scss" scoped>
.sidebar-item {
  background-color: rgb(var(--v-theme-treeview));
  border-left: 5px solid rgb(var(--v-theme-treeview));
}

.sidebar-item-focused {
  background-color: rgb(var(--v-theme-focus1));
  border-left: 5px solid rgb(var(--v-theme-primary));
}
</style>
