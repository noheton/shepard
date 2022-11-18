<script setup lang="ts">
import CollectionModal from "@/components/dataobjects/CollectionModal.vue";
import DataObjectList from "@/components/dataobjects/DataObjectList.vue";
import DataObjectModal from "@/components/dataobjects/DataObjectModal.vue";
import DeleteConfirmationModal from "@/components/DeleteConfirmationModal.vue";
import CreatedByLine from "@/components/generic/CreatedByLine.vue";
import GenericCollapse from "@/components/generic/GenericCollapse.vue";
import GenericDescription from "@/components/generic/GenericDescription.vue";
import PermissionsModal from "@/components/PermissionsModal.vue";
import CollectionService from "@/services/collectionService";
import { handleError, logError } from "@/utils/error-handling";
import type {
  Collection,
  Permissions,
  ResponseError,
  Roles,
} from "@dlr-shepard/shepard-client";
import { useTitle } from "@vueuse/core";
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue2-helpers/vue-router";
import CurrentRoleIcon from "../components/generic/CurrentRoleIcon.vue";

const router = useRouter();
const currentCollectionId = computed(() => {
  return Number(router.currentRoute.params.collectionId);
});

const currentCollection = ref<Collection | undefined>();
const attributeItems = ref<Array<{ key: string; value: string }>>([]);
function retrieveCollection() {
  CollectionService.getCollection({
    collectionId: currentCollectionId.value,
  })
    .then(response => {
      currentCollection.value = response;
      attributeItems.value = [];
      if (currentCollection.value.attributes !== undefined) {
        Object.entries(currentCollection.value.attributes).forEach(
          ([key, value]) =>
            attributeItems.value.push({ key: key, value: value }),
        );
      }
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching collection");
    });
}

const permissions = ref<Permissions | undefined>();
function retrievePermissions() {
  CollectionService.getCollectionPermissions({
    collectionId: currentCollectionId.value,
  })
    .then(response => {
      permissions.value = response;
    })
    .catch(e => {
      logError(e as ResponseError, "fetching permissions");
    });
}
function updatePermissions(perms: Permissions) {
  CollectionService.editCollectionPermissions({
    collectionId: currentCollectionId.value,
    permissions: perms,
  })
    .then(response => {
      permissions.value = response;
    })
    .catch(e => {
      handleError(e as ResponseError, "updating permissions");
    });
}

const roles = ref<Roles | undefined>();
function retrieveRoles() {
  CollectionService.getCollectionRoles({
    collectionId: currentCollectionId.value,
  })
    .then(response => {
      roles.value = response;
    })
    .catch(e => {
      logError(e as ResponseError, "fetching roles");
    });
}

function handleDelete() {
  CollectionService.deleteCollection({
    collectionId: currentCollectionId.value,
  })
    .then(() => {
      router.push({ name: "Explore" });
    })
    .catch(e => {
      handleError(e as ResponseError, "deleting collection");
    });
}

const title = computed(() => {
  return currentCollection.value?.name || "Collection";
});
function updateTitle() {
  useTitle(title, {
    titleTemplate: "%s | shepard",
  });
}

onMounted(() => {
  retrieveCollection();
  retrieveRoles();
  updateTitle();
});
</script>

<template>
  <div v-if="currentCollection" class="collection">
    <div>
      <b-button-group v-if="roles?.owner || roles?.writer" class="float-right">
        <b-button
          v-b-modal.create-data-object-modal
          v-b-tooltip.hover
          title="Create"
          variant="primary"
        >
          <CreateIcon />
        </b-button>
        <b-button
          v-b-modal.edit-collection-modal
          v-b-tooltip.hover
          title="Edit"
          variant="secondary"
        >
          <EditIcon />
        </b-button>
        <b-button
          v-if="roles?.owner || roles?.manager"
          v-b-modal.permissions-modal
          v-b-tooltip.hover
          title="Edit Permissions"
          variant="secondary"
          @click="retrievePermissions()"
        >
          <PermissionsIcon />
        </b-button>
        <b-button
          v-b-modal.delete-confirmation-modal
          v-b-tooltip.hover
          title="Delete"
          variant="info"
        >
          <DeleteIcon />
        </b-button>
      </b-button-group>
      <h3>
        {{ currentCollection.name }}
        <CurrentRoleIcon :roles="roles" />
      </h3>
    </div>
    <div class="mb-3">
      Collection ID: {{ currentCollection.id }}
      <CreatedByLine
        :created-at="currentCollection.createdAt"
        :created-by="currentCollection.createdBy"
        tooltip
      />
      <CreatedByLine
        v-if="currentCollection.updatedAt && currentCollection.updatedBy"
        :created-at="currentCollection.updatedAt"
        :created-by="currentCollection.updatedBy"
        updated
        tooltip
      />
    </div>

    <GenericDescription
      v-if="currentCollection.description"
      :text="currentCollection.description"
    />

    <GenericCollapse v-if="attributeItems.length" title="Attributes">
      <b-table striped small :items="attributeItems"> </b-table>
    </GenericCollapse>

    <GenericCollapse
      v-if="currentCollection.dataObjectIds?.length"
      title="Data Objects"
    >
      <DataObjectList
        v-if="currentCollection.id"
        :current-collection-id="currentCollection.id"
        :parent-id="-1"
      />
    </GenericCollapse>

    <CollectionModal
      :current-collection="currentCollection"
      modal-id="edit-collection-modal"
      modal-name="Edit Collection"
      @collection-changed="retrieveCollection()"
    />
    <DataObjectModal
      :current-collection-id="currentCollectionId"
      modal-id="create-data-object-modal"
      modal-name="Create Data Object"
    />
    <DeleteConfirmationModal
      modal-id="delete-confirmation-modal"
      modal-name="Confirm to delete collection"
      :modal-text="
        'Do you really want do delete the collection with name ' +
        currentCollection.name +
        '?'
      "
      @confirmation="handleDelete()"
    />
    <PermissionsModal
      modal-id="permissions-modal"
      modal-name="Edit Permissions"
      :entity-id="currentCollectionId"
      :old-permissions="permissions"
      @update="updatePermissions($event)"
    />
  </div>
</template>
