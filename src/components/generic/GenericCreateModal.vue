<template>
  <b-modal
    :id="modalId"
    ref="modal"
    size="lg"
    :title="modalName"
    lazy
    @show="handlePrepare()"
    @ok="handleOK()"
  >
    <b-form-group>
      <b-container>
        <b-row class="mb-3">
          <b-col cols="3"> Name </b-col>
          <b-col cols="9">
            <b-form-input
              v-model="newObject.name"
              placeholder="Name"
              required
            ></b-form-input>
          </b-col>
        </b-row>
      </b-container>
    </b-form-group>
  </b-modal>
</template>

<script lang="ts">
import Vue from "vue";

interface GenericCreateModelData {
  newObject: Record<string, unknown>;
}

function initialState(): GenericCreateModelData {
  return {
    newObject: {
      name: "",
    },
  };
}

export default Vue.extend({
  props: {
    modalId: {
      type: String,
      default: "GenericCreateModal",
    },
    modalName: {
      type: String,
      default: "GenericCreateModal",
    },
  },

  data() {
    return initialState();
  },

  methods: {
    handlePrepare() {
      Object.assign(this.$data, initialState());
    },
    handleOK() {
      this.$emit("create", this.newObject.name);
    },
  },
});
</script>
