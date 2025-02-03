<script lang="ts" setup>
import type { Collection } from "@dlr-shepard/backend-client";

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
          </template>
        </div>
      </template>
    </v-card-item>
  </v-card>
</template>
