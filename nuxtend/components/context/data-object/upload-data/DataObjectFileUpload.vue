<script setup lang="ts">
interface FileUploadDialogProps {
  collectionId: number;
  dataobjectId: number;
}

const props = defineProps<FileUploadDialogProps>();

const showDataObjectFileUploadDialog = ref<boolean>(false);

const files = ref<File | File[] | undefined>(undefined);
</script>

<template>
  <v-file-upload
    v-model:model-value="files"
    class="my-4"
    :accept="true"
    clearable
    icon="mdi-folder-upload-outline"
    :multiple="true"
    show-size
    density="compact"
    title="Drag and drop files here or click to upload"
    @click.stop.prevent.self="showDataObjectFileUploadDialog = true"
    @update:model-value="showDataObjectFileUploadDialog = true"
  >
    <!-- Overwrite file upload items here, since we do not need them-->
    <template #item="{}" />
  </v-file-upload>
  <DataObjectFileUploadDialog
    v-if="showDataObjectFileUploadDialog"
    v-model:show-dialog="showDataObjectFileUploadDialog"
    v-model:files="files"
    :initial-files="files"
    :collection-id="props.collectionId"
    :dataobject-id="props.dataobjectId"
    @upload-finished="files = undefined"
  />
</template>

<style lang="scss" scoped>
:deep(.v-file-upload) {
  border-color: rgb(var(--v-theme-medium-emphasis)) !important;
  background-color: rgb(var(--v-theme-canvas));
}

:deep(.v-file-upload-title) {
  color: rgb(var(--v-theme-textbody1)) !important;
}

:deep(.v-list-item) {
  border-color: rgb(var(--v-theme-textbody1)) !important;
}
</style>
