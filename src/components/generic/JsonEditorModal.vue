<template>
  <div>
    <b-modal
      :id="modalId"
      ref="modal"
      :title="modalName"
      size="lg"
      lazy
      ok-only
      @show="retrievePayload()"
    >
      <div id="jsoneditor"></div>
    </b-modal>
  </div>
</template>

<script lang="ts">
import StructuredDataService from "@/services/structuredDataService";
import { handleError } from "@/utils/error-handling";
import type { ResponseError } from "@dlr-shepard/shepard-client";
import JSONEditor, { type JSONEditorOptions } from "jsoneditor";
import "jsoneditor/dist/jsoneditor.css";
import { defineComponent } from "vue";

interface JsonEditorData {
  structuredDataPayload?: string;
  jsoneditor?: JSONEditor;
}

function initialState(): JsonEditorData {
  return {
    structuredDataPayload: undefined,
    jsoneditor: undefined,
  };
}

export default defineComponent({
  props: {
    modalId: {
      type: String,
      default: "JsonEditorModal",
    },
    modalName: {
      type: String,
      default: "JsonEditorModal",
    },
    containerId: {
      type: Number,
      required: true,
    },
    oid: {
      type: String,
      required: true,
    },
  },

  data() {
    return initialState();
  },
  methods: {
    retrievePayload() {
      StructuredDataService.getStructuredData({
        structureddataContainerId: this.containerId,
        oid: this.oid,
      })
        .then(response => {
          if (response.payload) this.structuredDataPayload = response.payload;
          this.startJsonEditor();
        })
        .catch(e => {
          handleError(e as ResponseError, "fetching structured data payload");
        });
    },

    startJsonEditor() {
      // create the editor
      const container = document.getElementById("jsoneditor");
      const options = {
        mode: "view",
        modes: ["code", "view"], // allowed modes
      } as JSONEditorOptions;

      if (container) {
        this.jsoneditor = new JSONEditor(container, options);
      } else {
        this.jsoneditor = undefined;
      }

      // set json
      if (this.jsoneditor && this.structuredDataPayload) {
        this.jsoneditor.set(JSON.parse(this.structuredDataPayload));
      }
    },
  },
});
</script>

<style>
#jsoneditor {
  height: 600px;
  border-color: dark;
}

.jsoneditor {
  border: thin solid #343a40;
}

.jsoneditor-menu {
  background-color: #343a40;
  border-bottom: 1px solid #343a40;
}
</style>
