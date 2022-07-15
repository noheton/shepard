<template>
  <b-modal
    :id="modalId"
    ref="modal"
    size="lg"
    :title="modalName"
    lazy
    @show="reset()"
    @ok="$emit('create', newURIReference)"
  >
    <b-form-group>
      <b-container>
        <b-row class="mb-3">
          <b-col cols="3"> Name </b-col>
          <b-col cols="9">
            <b-form-input
              v-model="newURIReference.name"
              placeholder="Name"
              required
            ></b-form-input>
          </b-col>
        </b-row>

        <b-row class="mb-3">
          <b-col cols="3"> URI </b-col>
          <b-col cols="9">
            <b-form-input
              v-model="newURIReference.uri"
              placeholder="URI"
              type="url"
              required
            ></b-form-input>
          </b-col>
        </b-row>
      </b-container>
    </b-form-group>
  </b-modal>
</template>

<script lang="ts">
import type { URIReference } from "@dlr-shepard/shepard-client";
import { defineComponent } from "vue";

interface URIReferenceModelData {
  newURIReference: URIReference;
}

function initialState(): URIReferenceModelData {
  return {
    newURIReference: {
      name: "",
      uri: "",
    },
  };
}

export default defineComponent({
  props: {
    modalId: {
      type: String,
      default: "URIReferenceModal",
    },
    modalName: {
      type: String,
      default: "URIReferenceModal",
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
