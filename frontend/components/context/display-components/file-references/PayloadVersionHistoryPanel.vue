<!--
  UI7 — PayloadVersionHistoryPanel

  Shows the byte-level version history for a named file in a FileContainer
  as a Vuetify expansion panel embedded in the FileReference detail page.

  Props:
    containerAppId  — UUID v7 of the FileContainer.
    containerId     — integer DB id of the FileContainer (needed for download).
    fileName        — the file name as uploaded (originalName).

  Calls:
    GET /v2/file-containers/{containerAppId}/files/{fileName}/versions
    GET /shepard/api/fileContainers/{containerId}/files/{oid}   (download)
-->
<script setup lang="ts">
import { FileContainerApi } from "@dlr-shepard/backend-client";
import { useFetchPayloadVersions } from "~/composables/container/useFetchPayloadVersions";
import type { PayloadVersionIO } from "~/composables/container/useFetchPayloadVersions";
import { useShepardApi } from "~/composables/common/api/useShepardApi";

const props = defineProps<{
  containerAppId: string;
  containerId: number;
  fileName: string;
}>();

const { versions, isLoading, error, load } = useFetchPayloadVersions(
  props.containerAppId,
  props.fileName,
);

// Fetch version history eagerly on component mount.
// The panel is collapsed by default so the data is ready when the user opens it.
onMounted(() => { load(); });

const api = useShepardApi(FileContainerApi);
const downloadingOid = ref<string | null>(null);

async function downloadVersion(version: PayloadVersionIO) {
  if (!version.fileOid) return;
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

function fmtBytes(b: number | null | undefined): string {
  if (b === null || b === undefined) return "—";
  if (b === 0) return "0 B";
  if (b < 1_048_576) return `${(b / 1_024).toFixed(1)} KB`;
  if (b < 1_073_741_824) return `${(b / 1_048_576).toFixed(1)} MB`;
  return `${(b / 1_073_741_824).toFixed(2)} GB`;
}

function sha256Short(hash: string | null | undefined): string {
  if (!hash) return "—";
  return hash.slice(0, 12) + "…";
}

const headers = [
  { title: "Version", key: "versionNumber", width: "90px", sortable: false },
  { title: "Size", key: "sizeBytes", sortable: false },
  { title: "Uploaded By", key: "uploadedBy", sortable: false },
  { title: "Uploaded At", key: "uploadedAt", sortable: false },
  { title: "SHA-256", key: "sha256", sortable: false },
  { title: "Download", key: "actions", sortable: false, width: "80px" },
];
</script>

<template>
  <ExpansionPanels :default-open="[]">
    <ExpansionPanelItem title="Version History" :count="versions.length || undefined">
      <!-- Loading skeleton -->
      <div v-if="isLoading" class="pa-4 d-flex align-center ga-2">
        <v-progress-circular indeterminate size="20" aria-label="Loading version history" />
        <span class="text-body-2 text-medium-emphasis">Loading version history…</span>
      </div>

      <!-- Error state -->
      <v-alert
        v-else-if="error"
        type="error"
        variant="tonal"
        density="compact"
        class="ma-2"
      >
        {{ error }}
      </v-alert>

      <!-- Empty state -->
      <div
        v-else-if="versions.length === 0"
        class="pa-4 text-body-2 text-medium-emphasis"
      >
        No version history available. Version records are created from the first
        upload; files uploaded before PV1a shipped will not have history entries.
      </div>

      <!-- Version table -->
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

        <template #[`item.sizeBytes`]="{ item }">
          <span class="font-weight-medium">{{ fmtBytes(item.sizeBytes) }}</span>
        </template>

        <template #[`item.uploadedAt`]="{ item }">
          {{ item.uploadedAt ? toShortDateString(new Date(item.uploadedAt)) : "—" }}
        </template>

        <template #[`item.sha256`]="{ item }">
          <v-tooltip :text="item.sha256 ?? 'not recorded'" location="top">
            <template #activator="{ props: tp }">
              <span
                v-bind="tp"
                class="text-mono text-caption text-medium-emphasis"
                style="cursor: help; font-family: monospace"
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
                : 'Download not available — uploaded via presigned URL'
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
                  :aria-label="`Download version ${item.versionNumber}`"
                  @click="downloadVersion(item)"
                />
              </span>
            </template>
          </v-tooltip>
        </template>
      </v-data-table>
    </ExpansionPanelItem>
  </ExpansionPanels>
</template>
