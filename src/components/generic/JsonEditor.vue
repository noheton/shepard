<script setup lang="ts">
import JSONEditor, { type JSONEditorOptions } from "jsoneditor";
import { onMounted, ref } from "vue";

const jsoneditor = ref<JSONEditor>();

const props = defineProps({
  payload: {
    type: String,
    required: true,
  },
});

function startJsonEditor() {
  // create the editor
  const container = document.getElementById("jsoneditor");
  const options: JSONEditorOptions = {
    mode: "view",
    modes: ["code", "view"], // allowed modes
  };

  if (container) {
    jsoneditor.value = new JSONEditor(container, options);
  } else {
    jsoneditor.value = undefined;
  }

  // set json
  if (jsoneditor.value && props.payload) {
    jsoneditor.value.set(JSON.parse(props.payload));
  }
}

onMounted(() => {
  startJsonEditor();
});
</script>

<template>
  <div>
    <div id="jsoneditor"></div>
  </div>
</template>

<style>
#jsoneditor {
  height: 600px;
}
</style>
