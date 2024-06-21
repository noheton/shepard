<script setup lang="ts">
import { createEditor } from "@/components/search/rete/editor";
import { ref } from "vue";

const props = defineProps({
  modalId: {
    type: String,
    default: "collectionModal",
  },
  modalName: {
    type: String,
    default: "collectionModal",
  },
  input: {
    type: String,
    default: "{}",
  },
});

const emit = defineEmits(["changed"]);

const container = ref<HTMLElement | null>();
const editor = ref<{
  get: () => Promise<Record<string, string>>;
  destroy: () => void;
}>();
async function prepare() {
  if (container.value) {
    editor.value = await createEditor(container.value, JSON.parse(props.input));
  }
}

async function handleOk() {
  if (editor.value) emit("changed", await editor.value.get());
}
</script>

<template>
  <b-modal
    :id="modalId"
    ref="modal"
    size="xl"
    :title="modalName"
    lazy
    @shown="prepare()"
    @ok="handleOk()"
  >
    <div ref="container" class="rete"></div>
  </b-modal>
</template>

<style lang="scss">
.rete {
  width: 100%;
  height: 700px;
}

.node {
  background: var(--dark) !important;
  border-color: var(--dark) !important;
}
.node.selected {
  background: var(--primary) !important;
  border-color: var(--primary) !important;
}

.socket {
  background: var(--dark) !important;
  border-color: var(--light) !important;
}

svg path {
  stroke: var(--primary) !important;
}

.block {
  background: var(--primary) !important;
  border-color: var(--white) !important;
}

.title {
  margin-top: 10px;
  margin-bottom: 10px;
}
</style>
