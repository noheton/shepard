<script lang="ts" setup>
interface FileUploadDialogProps {
  collectionId: number;
  dataobjectId: number;
}

const props = defineProps<FileUploadDialogProps>();

const showDataObjectFileUploadDialog = ref<boolean>(false);

const files = ref<File[]>();
</script>

<template>
  <v-file-upload
    v-model:model-value="files"
    :accept="true"
    :multiple="true"
    class="my-4"
    clearable
    density="compact"
    icon="mdi-folder-upload-outline"
    show-size
    title="Drag and drop files here or click to upload"
    @click.stop.prevent.capture="showDataObjectFileUploadDialog = true"
    @update:model-value="showDataObjectFileUploadDialog = true"
  >
    <!-- Overwrite file upload items here, since we do not need them-->
    <template #item="{}" />
  </v-file-upload>
  <DataObjectFileUploadDialog
    v-if="showDataObjectFileUploadDialog"
    v-model:files="files"
    v-model:show-dialog="showDataObjectFileUploadDialog"
    :collection-id="props.collectionId"
    :create-reference="true"
    :dataobject-id="props.dataobjectId"
    :initial-files="files"
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
