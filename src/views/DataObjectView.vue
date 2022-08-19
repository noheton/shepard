<template>
  <div v-if="currentDataObject" class="dataObject">
    <b-button-group class="float-right">
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
        variant="light"
      >
        <EditIcon />
      </b-button>
      <b-button
        v-b-modal.data-object-delete-confirmation-modal
        v-b-tooltip.hover
        title="Delete"
        variant="dark"
      >
        <DeleteIcon />
      </b-button>
    </b-button-group>

    <h3>{{ currentDataObject.name }}</h3>
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
        :collection-id="currentCollectionId"
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
      :current-collection-id="currentCollectionId"
      :current-data-object="currentDataObject"
      modal-id="edit-dataObject-modal"
      modal-name="Edit Data Object"
      @data-object-changed="retrieveDataObject()"
    />
    <DataObjectModal
      :current-collection-id="currentCollectionId"
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

<script lang="ts">
import DataObjectElement from "@/components/dataobjects/DataObjectElement.vue";
import DataObjectModal from "@/components/dataobjects/DataObjectModal.vue";
import RelatedObjectsTable from "@/components/dataobjects/RelatedObjectsTable.vue";
import DeleteConfirmationModal from "@/components/DeleteConfirmationModal.vue";
import CreatedByLine from "@/components/generic/CreatedByLine.vue";
import GenericCollapse from "@/components/generic/GenericCollapse.vue";
import GenericDescription from "@/components/generic/GenericDescription.vue";
import ReferencesTable from "@/components/references/ReferencesTable.vue";
import DataObjectService from "@/services/dataObjectService";
import { handleError } from "@/utils/error-handling";
import type { DataObject, ResponseError } from "@dlr-shepard/shepard-client";
import { defineComponent } from "vue";

interface DataObjectData {
  currentDataObject?: DataObject;
  attributeItems: Array<{ key: string; value: string }>;
  screenWidth: number;
}

export default defineComponent({
  components: {
    GenericCollapse,
    GenericDescription,
    CreatedByLine,
    DataObjectModal,
    ReferencesTable,
    RelatedObjectsTable,
    DataObjectElement,
    DeleteConfirmationModal,
  },
  data() {
    return {
      currentDataObject: undefined,
      attributeItems: [],
      screenWidth: 0,
    } as DataObjectData;
  },
  computed: {
    currentCollectionId(): number {
      return Number(this.$router.currentRoute.params.collectionId);
    },
    currentDataObjectId(): number {
      return Number(this.$router.currentRoute.params.dataObjectId);
    },
  },
  mounted() {
    this.retrieveDataObject();
    this.screenWidth = window.innerWidth;
  },
  methods: {
    scrollTo(element: string) {
      const el = this.$el.querySelector(element);
      if (el) {
        el.scrollIntoView();
      }
    },
    retrieveDataObject() {
      DataObjectService.getDataObject({
        collectionId: this.currentCollectionId,
        dataObjectId: this.currentDataObjectId,
      })
        .then(response => {
          this.currentDataObject = response;
          this.attributeItems = [];
          if (this.currentDataObject.attributes !== undefined) {
            Object.entries(this.currentDataObject.attributes).forEach(
              ([key, value]) =>
                this.attributeItems.push({ key: key, value: value }),
            );
          }
        })
        .catch(e => {
          handleError(e as ResponseError, "fetching data object");
        });
    },
    handleDelete() {
      DataObjectService.deleteDataObject({
        collectionId: this.currentCollectionId,
        dataObjectId: this.currentDataObjectId,
      })
        .then(() => {
          this.$router.push({
            name: "Collection",
            params: {
              collectionId: String(this.currentCollectionId),
            },
          });
        })
        .catch(e => {
          handleError(e as ResponseError, "deleting data object");
        });
    },
  },
});
</script>

<style scoped>
.section {
  margin-top: 30px;
  margin-bottom: 10px;
}
</style>
