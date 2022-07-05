<template>
  <div>
    <b-modal
      :id="modalId"
      ref="modal"
      size="lg"
      :title="modalName"
      @show="handlePrepare()"
      @shown="startJsonEditor()"
      @ok="handleOk()"
    >
      <b-form-group>
        <b-container>
          <b-row class="mb-3">
            <b-col cols="2"> Name </b-col>
            <b-col cols="10">
              <b-form-input
                v-model="newStructuredDataName"
                variant="primary"
                placeholder="Name"
                required
              >
              </b-form-input>
            </b-col>
          </b-row>

          <b-row class="mb-3">
            <b-col cols="2"> JSON </b-col>
            <b-col cols="10">
              <div id="jsoneditor"></div>
            </b-col>
          </b-row>
        </b-container>
      </b-form-group>
    </b-modal>
  </div>
</template>

<script lang="ts">
import { StructuredDataPayload } from "@dlr-shepard/shepard-client";
import JSONEditor, { JSONEditorOptions } from "jsoneditor";
import "jsoneditor/dist/jsoneditor.css";
import { defineComponent } from "vue";

interface CreateStructuredDataModalData {
  newStructuredDataPayload: StructuredDataPayload;
  newStructuredDataName: string;
  jsoneditor?: JSONEditor;
}

function initialState(): CreateStructuredDataModalData {
  return {
    newStructuredDataPayload: {},
    newStructuredDataName: "",
    jsoneditor: undefined,
  };
}

export default defineComponent({
  props: {
    modalId: {
      type: String,
      default: "CreateStructuredDataModal",
    },
    modalName: {
      type: String,
      default: "CreateStructuredDataModal",
    },
  },
  emits: ["created"],
  data() {
    return initialState();
  },
  methods: {
    handlePrepare() {
      initialState();
    },

    handleOk() {
      this.newStructuredDataPayload = {
        structuredData: { name: this.newStructuredDataName },
        payload: "{}",
      };
      this.newStructuredDataPayload.payload = JSON.stringify(
        this.jsoneditor?.get(),
      );

      this.$emit("created", this.newStructuredDataPayload);
    },

    startJsonEditor() {
      // create the editor
      const container = document.getElementById("jsoneditor");
      const options = {
        mode: "tree",
        modes: ["code", "tree"], // allowed modes
      } as JSONEditorOptions;
      if (container) {
        this.jsoneditor = new JSONEditor(container, options);
      } else {
        this.jsoneditor = undefined;
      }
    },
  },
});
</script>
