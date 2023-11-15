<script setup lang="ts">
import JSONEditor, { type JSONEditorOptions } from "jsoneditor";
import { onMounted, ref, watch } from "vue";

const jsoneditor = ref<JSONEditor>();

const props = defineProps({
  value: {
    type: String,
    required: true,
  },
});

const emits = defineEmits(["input"]);

watch(
  () => props.value,
  value => {
    const isSame = jsoneditor.value?.getText() === value;
    if (!isSame) jsoneditor.value?.setText(value);
  },
);

function startJsonEditor() {
  // create the editor
  const container = document.getElementById("jsoneditor");
  const options: JSONEditorOptions = {
    mode: "view",
    modes: ["code", "view"], // allowed modes
    onChangeText: text => {
      emits("input", text);
    },
  };

  if (container) {
    jsoneditor.value = new JSONEditor(container, options);
  } else {
    jsoneditor.value = undefined;
  }

  // set json
  if (jsoneditor.value && props.value) {
    jsoneditor.value.setText(props.value);
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
