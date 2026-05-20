<script setup lang="ts">
import { FileContainerApi } from "@dlr-shepard/backend-client";
import type { PayloadVersionIO } from "~/composables/container/useFetchPayloadVersions";
import { useFetchPayloadVersions } from "~/composables/container/useFetchPayloadVersions";
import { useShepardApi } from "~/composables/common/api/useShepardApi";

const props = defineProps<{
  containerAppId: string;
  containerId: number;
  fileName: string;
}>();

const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

const { versions, isLoading, error, load } = useFetchPayloadVersions(
  props.containerAppId,
  props.fileName,
);

watch(showDialog, open => {
  if (open) load();
});

const api = useShepardApi(FileContainerApi);

const downloadingOid = ref<string | null>(null);
const LARGE_FILE_THRESHOLD = 100 * 1_048_576; // 100 MB

async function downloadVersion(version: PayloadVersionIO) {
  if (!version.fileOid) return;

  // Warn before pulling large payloads synchronously through the backend.
  // Async prepare-and-notify (NTF1) will replace this for large files once
  // the notification system ships.
  if (version.sizeBytes && version.sizeBytes > LARGE_FILE_THRESHOLD) {
    const confirmed = window.confirm(
      `This version is ${fmtBytes(version.sizeBytes)}. Downloading streams the full ` +
      `payload through shepard — this may take time and put load on the server. Proceed?`,
    );
    if (!confirmed) return;
  }

  downloadingOid.value = version.fileOid;
  try {
    const blob = await api.value.getFile({
      fileContainerId: props.containerId,
      oid: version.fileOid,
    });
    downloadFile(blob, `${props.fileName}.v${version.versionNumber}`);
  } catch (e) {
    handleError(e as Error, "downloading version");
  } finally {
    downloadingOid.value = null;
  }
}

function fmtBytes(b: number | null): string {
  if (b === null || b === undefined) return "—";
  if (b === 0) return "0 B";
  if (b < 1_048_576) return `${(b / 1_024).toFixed(1)} KB`;
  if (b < 1_073_741_824) return `${(b / 1_048_576).toFixed(1)} MB`;
  return `${(b / 1_073_741_824).toFixed(2)} GB`;
}

function sha256Short(hash: string | null): string {
  if (!hash) return "—";
  return hash.slice(0, 16) + "…";
}

const headers = [
  { title: "Version", key: "versionNumber", width: "80px", sortable: false },
  { title: "Uploaded", key: "uploadedAt", sortable: false },
  { title: "By", key: "uploadedBy", sortable: false },
  { title: "Size", key: "sizeBytes", sortable: false },
  { title: "SHA-256", key: "sha256", sortable: false },
  { title: "", key: "actions", sortable: false, width: "60px" },
];
</script>

<template>
  <InformationDialog
    v-model:show-dialog="showDialog"
    :title="`Version history — ${fileName}`"
    :loading="isLoading"
    :max-width="860"
  >
    <template #text>
      <div v-if="error" class="text-error text-body-2 mb-2">{{ error }}</div>

      <div
        v-else-if="!isLoading && versions.length === 0"
        class="text-medium-emphasis text-body-2 pa-2"
      >
        No version records found for this file. Versions are recorded from
        first upload onwards; files uploaded before PV1a shipped will not have
        history entries.
      </div>

      <v-data-table
        v-else
        :headers="headers"
        :items="versions"
        :items-per-page="-1"
        density="compact"
        hide-default-footer
      >
        <template #[`item.versionNumber`]="{ item }">
          <v-chip size="x-small" variant="tonal" color="primary">
            v{{ item.versionNumber }}
          </v-chip>
        </template>

        <template #[`item.uploadedAt`]="{ item }">
          {{ item.uploadedAt ? toShortDateString(new Date(item.uploadedAt)) : "—" }}
        </template>

        <template #[`item.sizeBytes`]="{ item }">
          <span class="font-weight-medium">{{ fmtBytes(item.sizeBytes) }}</span>
        </template>

        <template #[`item.sha256`]="{ item }">
          <v-tooltip :text="item.sha256 ?? 'not recorded'" location="top">
            <template #activator="{ props: tp }">
              <span
                v-bind="tp"
                class="text-mono text-caption text-medium-emphasis"
                style="cursor: help"
              >
                {{ sha256Short(item.sha256) }}
              </span>
            </template>
          </v-tooltip>
        </template>

        <template #[`item.actions`]="{ item }">
          <v-tooltip
            :text="
              item.fileOid
                ? `Download v${item.versionNumber} (${fmtBytes(item.sizeBytes)})`
                : 'Download not available — uploaded via presigned URL without GridFS record'
            "
            location="top"
          >
            <template #activator="{ props: tp }">
              <span v-bind="tp">
                <v-btn
                  :disabled="!item.fileOid"
                  :loading="downloadingOid === item.fileOid"
                  icon="mdi-tray-arrow-down"
                  variant="plain"
                  size="small"
                  @click="downloadVersion(item)"
                />
              </span>
            </template>
          </v-tooltip>
        </template>
      </v-data-table>

      <div class="text-caption text-medium-emphasis mt-2">
        Downloads retrieve the full file payload from storage — check the size
        column first. For very large files, async prepare-and-notify download
        will be available once the notification system (NTF1) ships.
      </div>
    </template>
  </InformationDialog>
</template>
