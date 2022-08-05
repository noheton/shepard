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
      variant="dark"
      @dismissed="deletedAlert = false"
    >
      Successfully deleted
    </b-alert>

    <b-button
      v-b-modal.create-collection-ref-modal
      class="mb-3"
      variant="primary"
    >
      Create new Reference
    </b-button>

    <CollectionReferenceModal
      modal-id="create-collection-ref-modal"
      modal-name="Create Collection Reference"
      @create="create($event)"
    />

    <div v-if="collectionList == undefined"><Loading /></div>
    <b-list-group v-else>
      <b-list-group-item
        v-for="(collectionItem, index) in collectionList"
        :key="index"
      >
        <div>
          <b><GenericName :name="collectionItem.name || ''" /></b> | ID:
          {{ collectionItem.id }}
          <b-button
            v-b-modal.collection-reference-delete-confirmation-modal
            v-b-tooltip.hover
            class="float-right"
            title="Delete"
            variant="dark"
            @click="currentCollectionReference = collectionItem"
          >
            <DeleteIcon />
          </b-button>
        </div>
        <CreatedByLine
          :created-by="collectionItem.createdBy"
          :created-at="collectionItem.createdAt"
        />
        <small>
          {{ collectionItem.relationship }}:

          <span
            v-if="
              collectionItem.id && collectionItem.referencedCollectionId != -1
            "
          >
            <b-link
              v-if="referencedList[collectionItem.id]"
              :to="{
                name: 'Collection',
                params: {
                  collectionId: collectionItem.referencedCollectionId,
                },
              }"
            >
              <b>{{ referencedList[collectionItem.id].name }}</b> | ID:
              {{ referencedList[collectionItem.id].id }}
            </b-link>
          </span>
          <span v-else class="text-danger">Collection Deleted</span>
        </small>
      </b-list-group-item>
    </b-list-group>

    <DeleteConfirmationModal
      v-if="currentCollectionReference"
      modal-id="collection-reference-delete-confirmation-modal"
      modal-name="Confirm to delete Collection Reference"
      :modal-text="
        'Do you really want do delete the Collection Reference with name ' +
        currentCollectionReference.name +
        '?'
      "
      @confirmation="handleDelete()"
    />
  </div>
</template>

<script lang="ts">
import DeleteConfirmationModal from "@/components/DeleteConfirmationModal.vue";
import CreatedByLine from "@/components/generic/CreatedByLine.vue";
import GenericName from "@/components/generic/GenericName.vue";
import CollectionReferenceModal from "@/components/references/CollectionReferenceModal.vue";
import CollectionReferenceService from "@/services/collectionReferenceService";
import { emitter } from "@/utils/event-bus";
import type {
  Collection,
  CollectionReference,
} from "@dlr-shepard/shepard-client";
import { defineComponent } from "vue";
import Loading from "@/components/generic/Loading.vue";

interface CollectionListData {
  collectionList?: CollectionReference[];
  referencedList: { [key: number]: Collection };
  currentCollectionReference?: CollectionReference;
  createdAlert: boolean;
  deletedAlert: boolean;
}

export default defineComponent({
  components: {
    CreatedByLine,
    CollectionReferenceModal,
    DeleteConfirmationModal,
    GenericName,
    Loading,
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
      collectionList: undefined,
      referencedList: {},
      currentCollectionReference: undefined,
      createdAlert: false,
      deletedAlert: false,
    } as CollectionListData;
  },
  mounted() {
    this.retrieveReferences();
  },
  methods: {
    retrieveReferences() {
      CollectionReferenceService.getAllCollectionReferences({
        collectionId: this.currentCollectionId,
        dataObjectId: this.currentDataObjectId,
      })
        .then(response => {
          this.collectionList = response;
          response.forEach(reference => {
            if (reference.id) this.retrieveCollection(reference.id);
          });
        })
        .catch(e => {
          const error =
            "Error while fetching collection references: " + e.statusText;
          console.log(error);
        });
    },
    retrieveCollection(referenceId: number) {
      CollectionReferenceService.getCollectionReferencePayload({
        collectionId: this.currentCollectionId,
        dataObjectId: this.currentDataObjectId,
        collectionReferenceId: referenceId,
      })
        .then(response => {
          const temp: { [key: number]: Collection } = {};
          temp[referenceId] = response;
          this.referencedList = { ...this.referencedList, ...temp };
        })
        .catch(e => {
          const error =
            "Error while fetching collection reference payload: " +
            e.statusText;
          console.log(error);
        });
    },

    create(newReference: CollectionReference) {
      CollectionReferenceService.createCollectionReference({
        collectionId: this.currentCollectionId,
        dataObjectId: this.currentDataObjectId,
        collectionReference: newReference,
      })
        .then(response => {
          this.createdAlert = true;
          this.collectionList = [response].concat(this.collectionList || []);
          if (response.id) this.retrieveCollection(response.id);
        })
        .catch(e => {
          const error =
            "Error while creating collection reference: " + e.statusText;
          console.log(error);
          emitter.emit("error", error);
        });
    },

    handleDelete() {
      if (!this.currentCollectionReference?.id) return;
      CollectionReferenceService.deleteCollectionReference({
        collectionId: this.currentCollectionId,
        dataObjectId: this.currentDataObjectId,
        collectionReferenceId: this.currentCollectionReference.id,
      })
        .then(() => {
          this.deletedAlert = true;
          this.retrieveReferences();
        })
        .catch(e => {
          const error =
            "Error while deleting collection reference: " + e.statusText;
          console.log(error);
          emitter.emit("error", error);
        });
    },
  },
});
</script>
