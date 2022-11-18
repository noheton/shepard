<script setup lang="ts">
import DataObjectElement from "@/components/dataobjects/DataObjectElement.vue";
import DataObjectModal from "@/components/dataobjects/DataObjectModal.vue";
import RelatedObjectsTable from "@/components/dataobjects/RelatedObjectsTable.vue";
import DeleteConfirmationModal from "@/components/DeleteConfirmationModal.vue";
import CreatedByLine from "@/components/generic/CreatedByLine.vue";
import GenericCollapse from "@/components/generic/GenericCollapse.vue";
import GenericDescription from "@/components/generic/GenericDescription.vue";
import ReferencesTable from "@/components/references/ReferencesTable.vue";
import CollectionService from "@/services/collectionService";
import DataObjectService from "@/services/dataObjectService";
import { handleError, logError } from "@/utils/error-handling";
import type {
  DataObject,
  ResponseError,
  Roles,
} from "@dlr-shepard/shepard-client";
import { useTitle } from "@vueuse/core";
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue2-helpers/vue-router";
import CurrentRoleIcon from "../components/generic/CurrentRoleIcon.vue";

const route = useRoute();
const router = useRouter();

const currentDataObject = ref<DataObject>();
const attributeItems = ref<Array<{ key: string; value: string }>>([]);

const currentCollectionId = computed<string>(() => route.params.collectionId);
const currentDataObjectId = computed<string>(() => route.params.dataObjectId);

const root = ref<HTMLElement | undefined>();
function scrollTo(element: string) {
  const el = root.value?.querySelector(element);
  el?.scrollIntoView();
}
function retrieveDataObject() {
  DataObjectService.getDataObject({
    collectionId: +currentCollectionId.value,
    dataObjectId: +currentDataObjectId.value,
  })
    .then(response => {
      currentDataObject.value = response;
      attributeItems.value = [];
      if (currentDataObject.value.attributes !== undefined) {
        Object.entries(currentDataObject.value.attributes).forEach(
          ([key, value]) =>
            attributeItems.value.push({ key: key, value: value }),
        );
      }
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching data object");
    });
}

const roles = ref<Roles | undefined>();
function retrieveRoles() {
  CollectionService.getCollectionRoles({
    collectionId: +currentCollectionId.value,
  })
    .then(response => {
      roles.value = response;
    })
    .catch(e => {
      logError(e as ResponseError, "fetching roles");
    });
}

function handleDelete() {
  DataObjectService.deleteDataObject({
    collectionId: +currentCollectionId.value,
    dataObjectId: +currentDataObjectId.value,
  })
    .then(() => {
      router.push({
        name: "Collection",
        params: {
          collectionId: currentCollectionId.value,
        },
      });
    })
    .catch(e => {
      handleError(e as ResponseError, "deleting data object");
    });
}

const title = computed(() => {
  return currentDataObject.value?.name || "Data Object";
});
function updateTitle() {
  useTitle(title, {
    titleTemplate: "%s | shepard",
  });
}

onMounted(() => {
  retrieveDataObject();
  retrieveRoles();
  updateTitle();
});
</script>

<template>
  <div v-if="currentDataObject" ref="root" class="dataObject">
    <div>
      <b-button-group v-if="roles?.owner || roles?.writer" class="float-right">
        <b-button
          v-b-modal.create-dataObject-modal
          v-b-tooltip.hover
          title="Create"
          variant="primary"
        >
          <CreateIcon />
        </b-button>
        <b-button
          v-b-modal.edit-dataObject-modal
          v-b-tooltip.hover
          title="Edit"
          variant="secondary"
        >
          <EditIcon />
        </b-button>
        <b-button
          v-b-modal.data-object-delete-confirmation-modal
          v-b-tooltip.hover
          title="Delete"
          variant="info"
        >
          <DeleteIcon />
        </b-button>
      </b-button-group>

      <h3>
        {{ currentDataObject.name }}
        <CurrentRoleIcon :roles="roles" />
      </h3>
    </div>

    <div>
      Data Object ID: {{ currentDataObject.id }}
      <CreatedByLine
        :created-at="currentDataObject.createdAt"
        :created-by="currentDataObject.createdBy"
        tooltip
      />
      <CreatedByLine
        v-if="currentDataObject.updatedAt && currentDataObject.updatedBy"
        :created-at="currentDataObject.updatedAt"
        :created-by="currentDataObject.updatedBy"
        updated
        tooltip
      />
    </div>

    <b-row class="section">
      <b-col @click="scrollTo('#parentCollapse')">
        <ParentIcon />
        <span v-if="currentDataObject.parentId"> 1 </span>
        <span v-else> 0 </span>
        Parents
      </b-col>
      <b-col @click="scrollTo('#relatedObjectsCollapse')">
        <ChildIcon />
        {{ currentDataObject.childrenIds?.length }} Children
      </b-col>
      <b-col @click="scrollTo('#relatedObjectsCollapse')">
        <PredecessorIcon />
        {{ currentDataObject.predecessorIds?.length }} Predecessors
      </b-col>
      <b-col @click="scrollTo('#relatedObjectsCollapse')">
        <SuccessorIcon />
        {{ currentDataObject.successorIds?.length }} Successors
      </b-col>
      <b-col @click="scrollTo('#referencesCollapse')">
        <ReferencesIcon />
        {{ currentDataObject.referenceIds?.length }} References
      </b-col>
    </b-row>

    <GenericDescription
      v-if="currentDataObject.description"
      :text="currentDataObject.description"
    />

    <GenericCollapse v-if="attributeItems.length" title="Attributes">
      <b-table striped small :items="attributeItems"> </b-table>
    </GenericCollapse>

    <GenericCollapse
      v-if="currentDataObject.parentId"
      id="parentCollapse"
      title="Parent"
    >
      <DataObjectElement
        :collection-id="+currentCollectionId"
        :data-object-id="currentDataObject.parentId"
      />
    </GenericCollapse>

    <GenericCollapse id="relatedObjectsCollapse" title="Related Objects">
      <RelatedObjectsTable :current-data-object="currentDataObject" />
    </GenericCollapse>

    <GenericCollapse id="referencesCollapse" title="References">
      <ReferencesTable :current-data-object="currentDataObject" />
    </GenericCollapse>

    <DataObjectModal
      :current-collection-id="+currentCollectionId"
      :current-data-object="currentDataObject"
      modal-id="edit-dataObject-modal"
      modal-name="Edit Data Object"
      @data-object-changed="retrieveDataObject()"
    />
    <DataObjectModal
      :current-collection-id="+currentCollectionId"
      :current-data-object="{ parentId: currentDataObject.id, name: '' }"
      modal-id="create-dataObject-modal"
      modal-name="Create Data Object"
    />
    <DeleteConfirmationModal
      modal-id="data-object-delete-confirmation-modal"
      modal-name="Confirm to delete data object"
      :modal-text="
        'Do you really want do delete the data object with name ' +
        currentDataObject.name +
        '?'
      "
      @confirmation="handleDelete()"
    />
  </div>
</template>

<style scoped>
.section {
  margin-top: 30px;
  margin-bottom: 10px;
}
</style>
