<script setup lang="ts">
import RichTextEditor from "../editor/RichTextEditor.vue";

defineProps<{ description: string }>();
const emit = defineEmits<{ (e: "descriptionChanged", value: string): void }>();
</script>

<template>
  <v-row>
    <v-col>
      <div class="text-body-2 text-textbody2 pb-2">Description</div>
      <RichTextEditor
        :model-value="description"
        :initial-content="description"
        is-editable
        class="editor-border"
        @update:model-value="
          newDescription => emit('descriptionChanged', newDescription)
        "
      />
    </v-col>
  </v-row>
</template>

<style lang="scss" scoped>
:deep(.editor-border) {
  border: 1px solid rgb(var(--v-theme-divider1));
  border-radius: 4px;
  padding-top: 4px;
}

:deep(.editor-is-editable) {
  border-radius: 0px;
  border-bottom: unset;
  border-left: unset;
  border-right: unset;
}

:deep(.ProseMirror-focused:focus-visible) {
  outline: 2px solid rgb(var(--v-theme-primary)) !important;
}
</style>
