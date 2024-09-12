<script setup lang="ts">
import CollectionService from "@/services/collectionService";
import DataObjectService from "@/services/dataObjectService";
import { logError } from "@/utils/error-handling";
import type {
  Collection,
  DataObject,
  DataObjectReference,
  ResponseError,
} from "@dlr-shepard/backend-client";
import { reactive } from "vue";

interface Option {
  text: string;
  value: number;
}

defineProps({
  modalId: {
    type: String,
    default: "DataObjectReferenceModal",
  },
  modalName: {
    type: String,
    default: "DataObjectReferenceModal",
  },
});

const initialState = (): {
  newDataObjectReference: DataObjectReference;
  validCollection: boolean | undefined;
  currentCollectionId: string;
  currentCollection: Collection | undefined;
  currentDataObjectId: string;
  currentDataObject: DataObject | undefined;
  options: Option[];
} => ({
  newDataObjectReference: {
    name: "",
    referencedDataObjectId: 0,
    relationship: "",
  },
  validCollection: undefined,
  currentCollectionId: "",
  currentCollection: undefined,
  currentDataObjectId: "",
  currentDataObject: undefined,
  options: [],
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
      formData.options = [];
      collection.dataObjectIds?.forEach(id => {
        formData.options.push({ text: String(id), value: id });
      });
      formData.options.sort((a, b) => {
        return a.value - b.value;
      });
    })
    .catch(e => {
      logError(e as ResponseError, "fetching collection");
      formData.currentCollection = undefined;
      formData.validCollection = false;
    });
}

function fetchDataObject() {
  DataObjectService.getDataObject({
    collectionId: +formData.currentCollectionId,
    dataObjectId: +formData.currentDataObjectId,
  })
    .then(dataObject => {
      formData.currentDataObject = dataObject;
      if (dataObject.id)
        formData.newDataObjectReference.referencedDataObjectId = dataObject.id;
    })
    .catch(e => {
      logError(e as ResponseError, "fetching data object");
      formData.currentDataObject = undefined;
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
    @ok="$emit('create', formData.newDataObjectReference)"
  >
    <b-form-group>
      <b-container>
        <b-row class="mb-3">
          <b-col cols="3"> Name </b-col>
          <b-col cols="9">
            <b-form-input
              v-model="formData.newDataObjectReference.name"
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
              placeholder="Referenced collection id"
              type="number"
              required
              :state="formData.validCollection"
              @blur="fetchCollection()"
            ></b-form-input>
            <small v-if="formData.currentCollection">
              <em> {{ formData.currentCollection.name }} </em>
            </small>
            <small v-else>Please enter a valid collection id</small>
          </b-col>
        </b-row>

        <b-row class="mb-3">
          <b-col cols="3"> Data Object </b-col>
          <b-col cols="9">
            <b-form-select
              v-model="formData.currentDataObjectId"
              required
              :disabled="!formData.currentCollection"
              :options="formData.options"
              @change="fetchDataObject()"
            >
            </b-form-select>
            <small v-if="formData.currentDataObject">
              <em> {{ formData.currentDataObject.name }} </em>
            </small>
            <small v-else>Please select a data object</small>
          </b-col>
        </b-row>

        <b-row class="mb-3">
          <b-col cols="3"> Relationship </b-col>
          <b-col cols="9">
            <b-form-input
              v-model="formData.newDataObjectReference.relationship"
              placeholder="Relationship"
              required
            ></b-form-input>
          </b-col>
        </b-row>
      </b-container>
    </b-form-group>
  </b-modal>
</template>
