<template>
  <b-modal
    :id="modalId"
    ref="modal"
    size="lg"
    :title="modalName"
    lazy
    @show="reset()"
    @ok="$emit('create', newCollectionReference)"
  >
    <b-form-group>
      <b-container>
        <b-row class="mb-3">
          <b-col cols="3"> Name </b-col>
          <b-col cols="9">
            <b-form-input
              v-model="newCollectionReference.name"
              placeholder="Name"
              required
            ></b-form-input>
          </b-col>
        </b-row>

        <b-row class="mb-3">
          <b-col cols="3"> Collection </b-col>
          <b-col cols="9">
            <b-form-input
              v-model="currentCollectionId"
              :state="validCollection"
              placeholder="Referenced collection id"
              type="number"
              required
              @blur="fetchCollection()"
            ></b-form-input>
            <small v-if="currentCollection">
              <em> {{ currentCollection.name }} </em>
            </small>
            <small v-else>Please enter a valid collection id</small>
          </b-col>
        </b-row>

        <b-row class="mb-3">
          <b-col cols="3"> Relationship </b-col>
          <b-col cols="9">
            <b-form-input
              v-model="newCollectionReference.relationship"
              placeholder="Relationship"
            ></b-form-input>
          </b-col>
        </b-row>
      </b-container>
    </b-form-group>
  </b-modal>
</template>

<script lang="ts">
import CollectionService from "@/services/collectionService";
import type {
  Collection,
  CollectionReference,
} from "@dlr-shepard/shepard-client";
import { defineComponent } from "vue";

interface CollectionReferenceModelData {
  newCollectionReference: CollectionReference;
  validCollection?: boolean;
  currentCollectionId: string;
  currentCollection?: Collection;
}

function initialState(): CollectionReferenceModelData {
  return {
    newCollectionReference: {
      name: "",
      referencedCollectionId: 0,
      relationship: "",
    },
    validCollection: undefined,
    currentCollectionId: "",
    currentCollection: undefined,
  };
}

export default defineComponent({
  props: {
    modalId: {
      type: String,
      default: "CollectionReferenceModal",
    },
    modalName: {
      type: String,
      default: "CollectionReferenceModal",
    },
  },
  data() {
    return initialState();
  },
  methods: {
    reset() {
      Object.assign(this.$data, initialState());
    },
    fetchCollection() {
      CollectionService.getCollection({
        collectionId: +this.currentCollectionId,
      })
        .then(collection => {
          this.currentCollection = collection;
          this.validCollection = true;
          if (collection.id)
            this.newCollectionReference.referencedCollectionId = collection.id;
        })
        .catch(e => {
          const error = "Error while fetching collection: " + e.statusText;
          console.log(error);
          this.currentCollection = undefined;
          this.validCollection = false;
        });
    },
  },
});
</script>
