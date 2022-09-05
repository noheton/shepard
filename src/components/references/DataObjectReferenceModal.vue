<template>
  <b-modal
    :id="modalId"
    ref="modal"
    size="lg"
    :title="modalName"
    lazy
    @show="reset()"
    @ok="$emit('create', newDataObjectReference)"
  >
    <b-form-group>
      <b-container>
        <b-row class="mb-3">
          <b-col cols="3"> Name </b-col>
          <b-col cols="9">
            <b-form-input
              v-model="newDataObjectReference.name"
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
              placeholder="Referenced collection id"
              type="number"
              required
              :state="validCollection"
              @blur="fetchCollection()"
            ></b-form-input>
            <small v-if="currentCollection">
              <em> {{ currentCollection.name }} </em>
            </small>
            <small v-else>Please enter a valid collection id</small>
          </b-col>
        </b-row>

        <b-row class="mb-3">
          <b-col cols="3"> Data Object </b-col>
          <b-col cols="9">
            <b-form-select
              v-model="currentDataObjectId"
              required
              :disabled="!currentCollection"
              :options="options"
              @change="fetchDataObject()"
            >
            </b-form-select>
            <small v-if="currentDataObject">
              <em> {{ currentDataObject.name }} </em>
            </small>
            <small v-else>Please select a data object</small>
          </b-col>
        </b-row>

        <b-row class="mb-3">
          <b-col cols="3"> Relationship </b-col>
          <b-col cols="9">
            <b-form-input
              v-model="newDataObjectReference.relationship"
              placeholder="Relationship"
              required
            ></b-form-input>
          </b-col>
        </b-row>
      </b-container>
    </b-form-group>
  </b-modal>
</template>

<script lang="ts">
import CollectionService from "@/services/collectionService";
import DataObjectService from "@/services/dataObjectService";
import { logError } from "@/utils/error-handling";
import type {
  Collection,
  DataObject,
  DataObjectReference,
  ResponseError,
} from "@dlr-shepard/shepard-client";
import { defineComponent } from "vue";

interface Option {
  text: string;
  value: number;
}

interface DataObjectReferenceModelData {
  newDataObjectReference: DataObjectReference;
  validCollection?: boolean;
  currentCollectionId: string;
  currentCollection?: Collection;
  currentDataObjectId: string;
  currentDataObject?: DataObject;
  options: Array<Option>;
}

function initialState(): DataObjectReferenceModelData {
  return {
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
  };
}

export default defineComponent({
  props: {
    modalId: {
      type: String,
      default: "DataObjectReferenceModal",
    },
    modalName: {
      type: String,
      default: "DataObjectReferenceModal",
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
          this.options = [];
          collection.dataObjectIds?.forEach(id => {
            this.options.push({ text: String(id), value: id });
          });
          this.options.sort((a, b) => {
            return a.value - b.value;
          });
        })
        .catch(e => {
          logError(e as ResponseError, "fetching collection");
          this.currentCollection = undefined;
          this.validCollection = false;
        });
    },
    fetchDataObject() {
      DataObjectService.getDataObject({
        collectionId: +this.currentCollectionId,
        dataObjectId: +this.currentDataObjectId,
      })
        .then(dataObject => {
          this.currentDataObject = dataObject;
          if (dataObject.id)
            this.newDataObjectReference.referencedDataObjectId = dataObject.id;
        })
        .catch(e => {
          logError(e as ResponseError, "fetching data object");
          this.currentDataObject = undefined;
        });
    },
  },
});
</script>
