<script setup lang="ts">
import JSONEditor, { type JSONEditorOptions } from "jsoneditor";
import "jsoneditor/dist/jsoneditor.css";
import { onMounted, ref, watch } from "vue";

const jsoneditor = ref<JSONEditor>();

const props = defineProps({
  value: {
    type: String,
    required: true,
  },
  readOnly: {
    type: Boolean,
    default: false,
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
    mode: "tree",
    modes: ["code", "tree"], // allowed modes
    search: false,
    onEditable: () => !props.readOnly,
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

.jsoneditor {
  border: solid thin var(--info);
  border-radius: 0.15rem;
}

.jsoneditor-menu {
  background-color: var(--info);
  border-bottom: solid thin var(--info);
}
</style>
