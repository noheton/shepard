<script lang="ts" setup>
import type { ShepardFile } from "@dlr-shepard/backend-client";

const props = defineProps<{
  files: ShepardFile[];
  isAllowedToEdit: boolean;
  loading: boolean;
  containerAppId?: string;
  containerId: number;
}>();

const emit = defineEmits<{
  (e: "download-file" | "delete-file", value: ShepardFile): void;
}>();

const headers = computed(() => [
  ...(props.containerAppId
    ? [{ title: "", key: "thumbnail", sortable: false, width: "64px" }]
    : []),
  { title: "Name", key: "filename", sortable: true, width: "38%" },
  { title: "Oid", key: "oid", sortable: true, width: "28%" },
  { title: "Created at", key: "createdAt", sortable: true },
  { title: "", value: "actions" },
]);

const containerAppIdDefined = computed(() => props.containerAppId ?? "");

const fileToDelete = ref<ShepardFile | undefined>(undefined);
const showFileDeleteConfirmDialog = ref<boolean>(false);
const fileForHistory = ref<ShepardFile | undefined>(undefined);
const showHistoryDialog = ref(false);

const deleteFile = (file: ShepardFile) => {
  fileToDelete.value = file;
  showFileDeleteConfirmDialog.value = true;
};

const openHistory = (file: ShepardFile) => {
  fileForHistory.value = file;
  showHistoryDialog.value = true;
};
</script>

<template>
  <div>
    <DataTable
      :items-per-page="10"
      :cell-props="{
        class: 'text-textbody1',
      }"
      :header-props="{
        class: 'text-subtitle-2 text-textbody1',
      }"
      :headers="(headers as any)"
      :items-for-pagination="files"
      :loading="loading"
    >
      <template
        v-if="containerAppId"
        #[`item.thumbnail`]="{ item }: { item: ShepardFile }"
      >
        <FileThumbnailCell
          v-if="item.oid"
          :container-app-id="containerAppIdDefined"
          :oid="item.oid"
        />
      </template>
      <template #[`item.oid`]="{ item }: { item: ShepardFile }">
        <CopyTextButton :text="item.oid" />
      </template>
      <template #[`item.createdAt`]="{ item }: { item: ShepardFile }">
        {{ item.createdAt ? toShortDateString(item.createdAt) : "-" }}
      </template>
      <template #[`item.actions`]="{ item }: { item: ShepardFile }">
        <ActionContainer>
          <ActionButton
            icon="mdi-tray-arrow-down"
            @click="() => emit('download-file', item)"
          />
          <ActionButton
            v-if="containerAppId"
            icon="mdi-history"
            @click="openHistory(item)"
          />
          <ActionButton
            v-if="isAllowedToEdit"
            icon="mdi-delete-outline"
            @click="deleteFile(item)"
          />
        </ActionContainer>
      </template>
    </DataTable>
    <ConfirmDeleteDialog
      v-if="showFileDeleteConfirmDialog && fileToDelete?.filename"
      v-model:show-dialog="showFileDeleteConfirmDialog"
      @confirmed="emit('delete-file', fileToDelete)"
    />
    <PayloadVersionHistoryDialog
      v-if="showHistoryDialog && fileForHistory?.filename && containerAppId"
      v-model:show-dialog="showHistoryDialog"
      :container-app-id="containerAppId"
      :container-id="props.containerId"
      :file-name="fileForHistory.filename"
    />
  </div>
</template>
