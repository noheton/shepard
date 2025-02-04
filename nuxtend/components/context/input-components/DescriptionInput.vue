<script setup lang="ts">
const description = defineModel<string>("description", {
  required: true,
});
</script>

<template>
  <v-row>
    <v-col>
      <div class="text-body-2 text-medium-emphasis pb-2">Description</div>
      <RichTextEditor
        v-model:model-value="description"
        :initial-content="description"
        is-editable
        class="editor-border"
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
