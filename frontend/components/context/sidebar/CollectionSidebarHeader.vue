<script lang="ts" setup>
import {
  CollectionApi,
  type Collection,
  type ResponseError,
} from "@dlr-shepard/backend-client";
import type { ContextMenuItem } from "~/components/common/ContextMenu.vue";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import { CollectionAccessor } from "~/composables/context/CollectionAccessor";
import { useMyRoleIn } from "~/composables/context/useMyRoleIn";

interface CollectionSidebarHeaderProps {
  isFocused: boolean;
  collection?: Collection;
  isAllowedToEditCollection?: boolean;
  isAllowedToEditPermissions?: boolean;
  isOwner?: boolean;
}

const props = defineProps<CollectionSidebarHeaderProps>();

// U1c2 — derive collectionAppId defensively: the generated Collection model
// does not yet declare appId but the wire shape carries it (same pattern as
// pages/collections/[collectionId]/index.vue).
const collectionAppId = computed<string | null>(() => {
  if (!props.collection) return null;
  const raw = (props.collection as unknown as { appId?: string | null }).appId;
  return raw ?? null;
});

const { roleIn, roleLabel } = useMyRoleIn(collectionAppId);
const showContextMenuButton = ref<boolean>(false);
const showEditDialog = ref(false);
const showDeleteDialog = ref(false);
const showEditPermissionsDialog = ref(false);

const contextMenuItems = computed(() => {
  const items: ContextMenuItem[] = [];
  if (props.isAllowedToEditCollection) {
    items.push({
      label: "Edit",
      icon: "mdi-pencil-outline",
      onClick: () => (showEditDialog.value = true),
    });
  }

  if (props.isAllowedToEditPermissions) {
    items.push({
      label: "Permissions",
      icon: "mdi-account-outline",
      onClick: () => (showEditPermissionsDialog.value = true),
    });
  }

  items.push({
    label: "Export to RO-Crate",
    icon: "mdi-tray-arrow-down",
    onClick: () => {
      exportCollection();
    },
  });

  if (props.isAllowedToEditCollection) {
    items.push({
      label: "Delete",
      icon: "mdi-delete-outline",
      onClick: () => (showDeleteDialog.value = true),
    });
  }

  return items;
});

const exportCollection = () => {
  if (props.collection === undefined) return;
  const filename = sanitizeFilename(props.collection.name) + ".zip";

  emitSuccess(
    "Export is being generated. Download will start automatically when it's ready.",
  );

  useShepardApi(CollectionApi)
    .value.exportCollection({ collectionId: props.collection.id })
    .then(response => {
      downloadFile(response, filename);
    })
    .catch(e => {
      handleError(e as ResponseError, "exporting collection");
    });
};
</script>

<template>
  <div
    class="ml-6 text-textbody1 text-overline"
    style="margin-top: 40px; margin-bottom: 4px"
  >
    Collection
  </div>
  <v-card
    :class="`d-flex ${isFocused ? 'sidebar-item-focused' : 'sidebar-item'}`"
    :to="collectionsPath + `${collection?.id}`"
    flat
    hover
    rounded="0"
    style="min-height: 40px; max-height: 40px"
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
            v-if="contextMenuItems.length > 0"
            style="flex-shrink: 0; margin-left: 10px"
          >
            <DisplayChildrenOnHover
              :display-children-without-hover="
                showContextMenuButton || showEditDialog
              "
            >
              <ContextMenu
                :items="contextMenuItems"
                @expansion-state-changed="
                  (e: boolean) => (showContextMenuButton = e)
                "
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
            <EditPermissionsDialog
              v-if="showEditPermissionsDialog"
              v-model:show-dialog="showEditPermissionsDialog"
              :shepard-object-accessor="new CollectionAccessor(collection.id)"
            />
          </div>
        </div>
      </template>
    </v-card-item>
  </v-card>

  <!-- U1c2: Role-in-context chips — rendered below the collection name card
       when the user has a role in this collection or is an instance admin. -->
  <div v-if="collectionAppId" class="ml-7 mt-1">
    <CollectionRoleChip
      :role-label="roleLabel"
      :is-instance-admin="roleIn?.isInstanceAdmin ?? false"
    />
  </div>
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
