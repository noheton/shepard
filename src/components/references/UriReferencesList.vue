<template>
  <div>
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

    <b-button v-b-modal.create-uri-ref-modal class="mb-3" variant="primary">
      Create new Reference
    </b-button>

    <UriReferenceModal
      modal-id="create-uri-ref-modal"
      modal-name="Create URI Reference"
      @create="create($event)"
    />

    <b-list-group>
      <b-list-group-item v-for="(uriItem, index) in uriList" :key="index">
        <div>
          <b><GenericName :name="uriItem.name" /></b> | ID:
          {{ uriItem.id }}
          <b-button
            v-b-modal.uri-reference-delete-confirmation-modal
            v-b-tooltip.hover
            class="float-right"
            title="Delete"
            variant="dark"
            @click="currentUriReference = uriItem"
          >
            <DeleteIcon />
          </b-button>
        </div>
        <CreatedByLine
          :created-by="uriItem.createdBy"
          :created-at="uriItem.createdAt"
        />
        <b-link :href="uriItem.uri">{{ uriItem.uri }}</b-link>
      </b-list-group-item>
    </b-list-group>

    <DeleteConfirmationModal
      v-if="currentUriReference"
      modal-id="uri-reference-delete-confirmation-modal"
      modal-name="Confirm to delete URI Reference"
      :modal-text="
        'Do you really want do delete the URI Reference with name ' +
        currentUriReference.name +
        '?'
      "
      @confirmation="handleDelete(currentUriReference.id)"
    />
  </div>
</template>

<script lang="ts">
import DeleteConfirmationModal from "@/components/DeleteConfirmationModal.vue";
import CreatedByLine from "@/components/generic/CreatedByLine.vue";
import GenericName from "@/components/generic/GenericName.vue";
import UriReferenceModal from "@/components/references/UriReferenceModal.vue";
import UriReferenceService from "@/services/uriReferenceService";
import { emitter } from "@/utils/event-bus";
import type { URIReference } from "@dlr-shepard/shepard-client";
import { defineComponent } from "vue";

interface URIListData {
  uriList: URIReference[];
  currentUriReference?: URIReference;
  createdAlert: boolean;
  deletedAlert: boolean;
}

export default defineComponent({
  components: {
    CreatedByLine,
    UriReferenceModal,
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
      uriList: new Array<URIReference>(),
      currentUriReference: undefined,
      createdAlert: false,
      deletedAlert: false,
    } as URIListData;
  },
  mounted() {
    this.retrieveReferences();
  },
  methods: {
    retrieveReferences() {
      UriReferenceService.getAllUriReferences({
        collectionId: this.currentCollectionId,
        dataObjectId: this.currentDataObjectId,
      })
        .then(response => {
          this.uriList = response;
        })
        .catch(e => {
          const error = "Error while fetching URI references: " + e.statusText;
          console.log(error);
        });
    },

    create(newReference: URIReference) {
      UriReferenceService.createUriReference({
        collectionId: this.currentCollectionId,
        dataObjectId: this.currentDataObjectId,
        uRIReference: newReference,
      })
        .then(response => {
          this.createdAlert = true;
          this.uriList = [response].concat(this.uriList);
        })
        .catch(e => {
          const error = "Error while creating URI reference: " + e.statusText;
          console.log(error);
          emitter.emit("error", error);
        });
    },

    handleDelete(uriReferenceId: number) {
      UriReferenceService.deleteUriReference({
        collectionId: this.currentCollectionId,
        dataObjectId: this.currentDataObjectId,
        uriReferenceId: uriReferenceId,
      })
        .then(() => {
          this.deletedAlert = true;
          this.retrieveReferences();
        })
        .catch(e => {
          const error = "Error while deleting URI reference: " + e.statusText;
          console.log(error);
          emitter.emit("error", error);
        });
    },
  },
});
</script>
