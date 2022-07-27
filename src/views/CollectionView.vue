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
import { emitter } from "@/utils/event-bus";
import { useRouter } from "@/utils/helpers";
import type {
  Collection,
  Permissions,
  Roles,
} from "@dlr-shepard/shepard-client";
import { computed, onMounted, ref, type Ref } from "vue";

const router = useRouter();
const currentCollectionId = computed(() => {
  return Number(router.currentRoute.params.collectionId);
});

const currentCollection: Ref<Collection | undefined> = ref();
const attributeItems: Ref<Array<{ key: string; value: string }>> = ref([]);
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
      const error = "Error while fetching collection: " + e.statusText;
      console.log(error);
      emitter.emit("error", error);
    });
}

const permissions: Ref<Permissions | undefined> = ref();
function retrievePermissions() {
  CollectionService.getCollectionPermissions({
    collectionId: currentCollectionId.value,
  })
    .then(response => {
      permissions.value = response;
    })
    .catch(e => {
      const error = "Error while fetching permissons: " + e.statusText;
      console.log(error);
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
      const error = "Error while updating permissons: " + e.statusText;
      console.log(error);
    });
}

const roles: Ref<Roles | undefined> = ref();
function retrieveRoles() {
  CollectionService.getCollectionRoles({
    collectionId: currentCollectionId.value,
  })
    .then(response => {
      roles.value = response;
    })
    .catch(e => {
      const error = "Error while fetching roles: " + e.statusText;
      console.log(error);
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
      const error = "Error while deleting collection: " + e.statusText;
      console.log(error);
      emitter.emit("error", error);
    });
}

onMounted(() => {
  retrieveCollection();
  retrieveRoles();
  retrievePermissions();
});
</script>

<template>
  <div v-if="currentCollection" class="collection">
    <div>
      <b-button-group
        v-if="!roles || roles.owner || roles.writer"
        class="float-right"
      >
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
          variant="light"
        >
          <EditIcon />
        </b-button>
        <b-button
          v-if="!roles || roles.owner || roles.manager"
          v-b-modal.permissions-modal
          v-b-tooltip.hover
          title="Edit Permissions"
          variant="light"
        >
          <PermissionsIcon />
        </b-button>
        <b-button
          v-b-modal.delete-confirmation-modal
          v-b-tooltip.hover
          title="Delete"
          variant="dark"
        >
          <DeleteIcon />
        </b-button>
      </b-button-group>
      <h3>
        {{ currentCollection.name }}
        <span v-if="roles">
          <ManagerIcon
            v-if="roles.owner || roles.manager"
            v-b-tooltip.hover
            title="Manager"
            font-scale="0.8"
          />
          <WriterIcon
            v-else-if="roles.writer"
            v-b-tooltip.hover
            title="Writer"
            font-scale="0.8"
          />
          <ReaderIcon
            v-else-if="roles.reader"
            v-b-tooltip.hover
            title="Reader"
            font-scale="0.8"
          />
        </span>
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
        v-if="currentCollection.updatedBy"
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

<style scoped>
.listElement {
  border: solid thin;
  border-color: #e3e3e3;
  border-radius: 5px;
  padding: 20px;
}
</style>
