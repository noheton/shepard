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
import { StructuredDataVue } from "@/utils/api-mixin";
import { emitter } from "@/utils/event-bus";
import JSONEditor, { JSONEditorOptions } from "jsoneditor";
import "jsoneditor/dist/jsoneditor.css";
import Vue, { VueConstructor } from "vue";

interface JsonEditorData {
  structuredDataPayload?: string;
  options: JSONEditorOptions;
}

function initialState(): JsonEditorData {
  return {
    structuredDataPayload: undefined,
    options: {},
  };
}

export default (
  Vue as VueConstructor<Vue & InstanceType<typeof StructuredDataVue>>
).extend({
  mixins: [StructuredDataVue],
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
      this.structuredDataApi
        ?.getStructuredData({
          structureddataContainerId: this.containerId,
          oid: this.oid,
        })
        .then(response => {
          if (response.payload) this.structuredDataPayload = response.payload;
          this.startJsonEditor();
        })
        .catch(e => {
          const error =
            "Error while fetching structured data payload: " + e.statusText;
          console.log(error);
          emitter.emit("error", error);
        });
    },

    startJsonEditor() {
      // create the editor
      const container = document.getElementById("jsoneditor");
      this.options = {
        mode: "view",
        modes: ["code", "view"], // allowed modes
      };
      let editor = undefined;
      if (container) {
        editor = new JSONEditor(container, this.options);
      } else {
        editor = undefined;
      }

      // set json
      if (editor && this.structuredDataPayload) {
        editor.set(JSON.parse(this.structuredDataPayload));
      }
    },
  },
});
</script>

<style type="text/css">
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
