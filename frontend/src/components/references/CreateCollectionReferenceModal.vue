<script setup lang="ts">
import type {
  Collection,
  CollectionReference,
  ResponseError,
} from "@/generated/openapi";
import CollectionService from "@/services/collectionService";
import { logError } from "@/utils/error-handling";
import { reactive } from "vue";

defineProps({
  modalId: {
    type: String,
    default: "CollectionReferenceModal",
  },
  modalName: {
    type: String,
    default: "CollectionReferenceModal",
  },
});

const initialState = (): {
  newCollectionReference: CollectionReference;
  validCollection: boolean | undefined;
  currentCollectionId: string;
  currentCollection: Collection | undefined;
} => ({
  newCollectionReference: {
    name: "",
    referencedCollectionId: 0,
    relationship: "",
  },
  validCollection: undefined,
  currentCollectionId: "",
  currentCollection: undefined,
});

const formData = reactive(initialState());

function reset() {
  Object.assign(formData, initialState());
}

function fetchCollection() {
  CollectionService.getCollection({
    collectionId: +formData.currentCollectionId,
  })
    .then(collection => {
      formData.currentCollection = collection;
      formData.validCollection = true;
      if (collection.id)
        formData.newCollectionReference.referencedCollectionId = collection.id;
    })
    .catch(e => {
      logError(e as ResponseError, "fetching collection");
      formData.currentCollection = undefined;
      formData.validCollection = false;
    });
}
</script>

<template>
  <b-modal
    :id="modalId"
    ref="modal"
    size="lg"
    :title="modalName"
    lazy
    @show="reset()"
    @ok="$emit('create', formData.newCollectionReference)"
  >
    <b-form-group>
      <b-container>
        <b-row class="mb-3">
          <b-col cols="3"> Name </b-col>
          <b-col cols="9">
            <b-form-input
              v-model="formData.newCollectionReference.name"
              placeholder="Name"
              required
            ></b-form-input>
          </b-col>
        </b-row>

        <b-row class="mb-3">
          <b-col cols="3"> Collection </b-col>
          <b-col cols="9">
            <b-form-input
              v-model="formData.currentCollectionId"
              :state="formData.validCollection"
              placeholder="Referenced collection id"
              type="number"
              required
              @blur="fetchCollection()"
            ></b-form-input>
            <small v-if="formData.currentCollection">
              <em> {{ formData.currentCollection.name }} </em>
            </small>
            <small v-else>Please enter a valid collection id</small>
          </b-col>
        </b-row>

        <b-row class="mb-3">
          <b-col cols="3"> Relationship </b-col>
          <b-col cols="9">
            <b-form-input
              v-model="formData.newCollectionReference.relationship"
              placeholder="Relationship"
            ></b-form-input>
          </b-col>
        </b-row>
      </b-container>
    </b-form-group>
  </b-modal>
</template>
