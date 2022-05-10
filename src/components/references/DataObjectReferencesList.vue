<template>
  <div class="list">
    <b-alert
      :show="createdAlert"
      dismissible
      variant="success"
      @dismissed="createdAlert = false"
    >
      Successfully created
    </b-alert>
    <b-alert
      :show="deletedAlert"
      dismissible
      variant="danger"
      @dismissed="deletedAlert = false"
    >
      Successfully deleted
    </b-alert>

    <b-button
      v-b-modal.create-data-object-ref-modal
      class="mb-3"
      variant="primary"
    >
      Create new Reference
    </b-button>

    <DataObjectReferenceModal
      modal-id="create-data-object-ref-modal"
      modal-name="Create DataObject Reference"
      @create="create($event)"
    />

    <b-list-group>
      <b-list-group-item
        v-for="(dataObjectItem, index) in dataObjectList"
        :key="index"
      >
        <div>
          <b><GenericName :name="dataObjectItem.name" /></b> | ID:
          {{ dataObjectItem.id }}
          <b-button
            v-b-modal.data-object-reference-delete-confirmation-modal
            v-b-tooltip.hover
            class="float-right"
            title="Delete"
            variant="dark"
            @click="currentDataObjectReference = dataObjectItem"
          >
            <DeleteIcon />
          </b-button>
        </div>
        <CreatedByLine
          :created-by="dataObjectItem.createdBy"
          :created-at="dataObjectItem.createdAt"
        />
        <small>
          {{ dataObjectItem.relationship }}:
          <span v-if="dataObjectItem.referencedDataObjectId != -1">
            <b-link
              v-if="referencedList[dataObjectItem.id]"
              :to="{
                name: 'DataObject',
                params: {
                  collectionId: referencedList[dataObjectItem.id].collectionId,
                  dataObjectId: dataObjectItem.referencedDataObjectId,
                },
              }"
            >
              <b>{{ referencedList[dataObjectItem.id].name }}</b> | ID:
              {{ referencedList[dataObjectItem.id].id }}
            </b-link>
          </span>
          <span v-else class="text-danger">DateObject Deleted</span>
        </small>
      </b-list-group-item>
    </b-list-group>

    <DeleteConfirmationModal
      v-if="currentDataObjectReference"
      modal-id="data-object-reference-delete-confirmation-modal"
      modal-name="Confirm to delete Data Object Reference"
      :modal-text="
        'Do you really want do delete the Data Object Reference with name ' +
        currentDataObjectReference.name +
        '?'
      "
      @confirmation="handleDelete(currentDataObjectReference.id)"
    />
  </div>
</template>

<script lang="ts">
import DeleteConfirmationModal from "@/components/DeleteConfirmationModal.vue";
import CreatedByLine from "@/components/generic/CreatedByLine.vue";
import GenericName from "@/components/generic/GenericName.vue";
import DataObjectReferenceModal from "@/components/references/DataObjectReferenceModal.vue";
import DataObjectReferenceService from "@/services/dataObjectReferenceService";
import { emitter } from "@/utils/event-bus";
import { DataObject, DataObjectReference } from "@dlr-shepard/shepard-client";
import Vue from "vue";

interface DataObjectReferenceListData {
  dataObjectList: DataObjectReference[];
  referencedList: { [key: number]: DataObject };
  currentDataObjectReference?: DataObjectReference;
  createdAlert: boolean;
  deletedAlert: boolean;
}

export default Vue.extend({
  components: {
    CreatedByLine,
    DataObjectReferenceModal,
    DeleteConfirmationModal,
    GenericName,
  },
  props: {
    currentCollectionId: {
      type: Number,
      required: true,
    },
    currentDataObjectId: {
      type: Number,
      required: true,
    },
  },
  data() {
    return {
      dataObjectList: new Array<DataObjectReference>(),
      referencedList: {},
      currentDataObjectReference: undefined,
      createdAlert: false,
      deletedAlert: false,
    } as DataObjectReferenceListData;
  },
  mounted() {
    this.retrieveReferences();
  },
  methods: {
    retrieveReferences() {
      DataObjectReferenceService.getAllDataObjectReferences({
        collectionId: this.currentCollectionId,
        dataObjectId: this.currentDataObjectId,
      })
        .then(response => {
          this.dataObjectList = response;
          response.forEach(reference => {
            if (reference.id) this.retrieveDataObject(reference.id);
          });
        })
        .catch(e => {
          const error =
            "Error while fetching data object references: " + e.statusText;
          console.log(error);
        });
    },
    retrieveDataObject(referenceId: number) {
      DataObjectReferenceService.getDataObjectReferencePayload({
        collectionId: this.currentCollectionId,
        dataObjectId: this.currentDataObjectId,
        dataObjectReferenceId: referenceId,
      })
        .then(response => {
          const temp: { [key: number]: DataObject } = {};
          temp[referenceId] = response;
          this.referencedList = { ...this.referencedList, ...temp };
        })
        .catch(e => {
          const error =
            "Error while fetching data object reference payload: " +
            e.statusText;
          console.log(error);
        });
    },

    create(newReference: DataObjectReference) {
      DataObjectReferenceService.createDataObjectReference({
        collectionId: this.currentCollectionId,
        dataObjectId: this.currentDataObjectId,
        dataObjectReference: newReference,
      })
        .then(response => {
          this.createdAlert = true;
          this.dataObjectList = [response].concat(this.dataObjectList);
          if (response.id) this.retrieveDataObject(response.id);
        })
        .catch(e => {
          const error =
            "Error while creating data object reference: " + e.statusText;
          console.log(error);
          emitter.emit("error", error);
        });
    },

    handleDelete(dataObjectReferenceId: number) {
      DataObjectReferenceService.deleteDataObjectReference({
        collectionId: this.currentCollectionId,
        dataObjectId: this.currentDataObjectId,
        dataObjectReferenceId: dataObjectReferenceId,
      })
        .then(() => {
          this.deletedAlert = true;
          this.retrieveReferences();
        })
        .catch(e => {
          const error =
            "Error while deleting data object reference: " + e.statusText;
          console.log(error);
          emitter.emit("error", error);
        });
    },
  },
});
</script>
