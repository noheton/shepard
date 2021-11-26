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
          <b-col cols="3"> Referenced Data Object </b-col>
          <b-col cols="9">
            <b-form-input
              v-model="newDataObjectReference.referencedDataObjectId"
              placeholder="Referenced data object id"
              type="number"
              required
              @blur="fetchDataObject()"
            ></b-form-input>
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
import { DataObjectVue } from "@/utils/api-mixin";
import { DataObjectReference } from "@dlr-shepard/shepard-client";
import Vue from "vue";

interface DataObjectReferenceModelData {
  newDataObjectReference: DataObjectReference;
}

function initialState(): DataObjectReferenceModelData {
  return {
    newDataObjectReference: {
      name: "",
      referencedDataObjectId: 0,
      relationship: "",
    },
  };
}

export default Vue.extend({
  mixins: [DataObjectVue],
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
  },
});
</script>
