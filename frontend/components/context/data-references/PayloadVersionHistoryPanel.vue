<script setup lang="ts">
/**
 * UI7 — PayloadVersion history expansion panel for the FileReference detail page.
 *
 * Renders a collapsed-by-default v-expansion-panels section listing all
 * byte-level versions for a single file inside a FileContainer.
 * Expanding the panel triggers the lazy fetch; the data is not loaded until
 * the user opens the panel.
 *
 * Columns: version number, SHA-256 (first 16 chars), size in bytes,
 * upload timestamp. Per-row download button calls the legacy GridFS
 * getFile endpoint (same as FilesTable / PayloadVersionHistoryDialog).
 *
 * Props:
 *   containerAppId — UUID v7 of the FileContainer (from useFetchFileReference).
 *   containerId    — numeric id of the FileContainer (for the download call).
 *   fileName       — filename as returned by ShepardFile.filename.
 *
 * Cross-references: PV1a backend; aidocs/16 UI7 row.
 */
import { FileContainerApi } from "@dlr-shepard/backend-client";
import type { PayloadVersionIO } from "~/composables/container/useFetchPayloadVersions";
import { useFetchPayloadVersions } from "~/composables/container/useFetchPayloadVersions";
import { useShepardApi } from "~/composables/common/api/useShepardApi";

const props = defineProps<{
  /** UUID v7 of the FileContainer; required to call the v2 versions endpoint. */
  containerAppId: string;
  /** Numeric FileContainer id; used by the legacy getFile download endpoint. */
  containerId: number;
  /** Filename as returned by ShepardFile.filename (the "originalName" key). */
  fileName: string;
}>();

// Expansion-panel state: -1 = collapsed (default), 0 = open
const openPanel = ref<number | undefined>(undefined);

const { versions, isLoading, error, load } = useFetchPayloadVersions(
  props.containerAppId,
  props.fileName,
);

// Lazily load versions on first expand
const hasLoaded = ref(false);
watch(openPanel, val => {
  if (val === 0 && !hasLoaded.value) {
    hasLoaded.value = true;
    void load();
  }
});

const api = useShepardApi(FileContainerApi);
const downloadingOid = ref<string | null>(null);

const LARGE_FILE_THRESHOLD = 100 * 1_048_576; // 100 MB

async function downloadVersion(version: PayloadVersionIO) {
  if (!version.fileOid) return;

  if (version.sizeBytes && version.sizeBytes > LARGE_FILE_THRESHOLD) {
    const confirmed = window.confirm(
      `This version is ${fmtBytes(version.sizeBytes)}. Downloading streams the ` +
      `full payload through shepard — this may take time. Proceed?`,
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

function fmtBytes(b: number | null | undefined): string {
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
  { title: "Version", key: "versionNumber", sortable: false, width: "90px" },
  { title: "Uploaded", key: "uploadedAt", sortable: false },
  { title: "Size", key: "sizeBytes", sortable: false },
  { title: "SHA-256", key: "sha256", sortable: false },
  { title: "", key: "actions", sortable: false, width: "56px" },
];
</script>

<template>
  <v-expansion-panels v-model="openPanel" variant="accordion" flat tile>
    <v-expansion-panel>
      <v-expansion-panel-title class="text-subtitle-2 px-0 py-3">
        <v-icon start size="18" class="mr-2">mdi-history</v-icon>
        Version history
        <v-chip
          v-if="!isLoading && versions.length > 0"
          size="x-small"
          variant="tonal"
          color="primary"
          class="ml-2"
        >
          {{ versions.length }}
        </v-chip>
      </v-expansion-panel-title>

      <v-expansion-panel-text class="px-0">
        <!-- Loading state -->
        <div v-if="isLoading" class="d-flex align-center justify-center pa-4">
          <v-progress-circular indeterminate size="24" color="primary" />
        </div>

        <!-- Error state -->
        <div
          v-else-if="error"
          class="text-error text-body-2 pa-2"
        >
          {{ error }}
        </div>

        <!-- Empty state -->
        <div
          v-else-if="versions.length === 0"
          class="text-medium-emphasis text-body-2 pa-2"
        >
          No version records found. Versions are recorded from first upload
          onwards; files uploaded before PV1a shipped will not have history
          entries.
        </div>

        <!-- Version table -->
        <v-data-table
          v-else
          :headers="headers"
          :items="versions"
          :items-per-page="-1"
          density="compact"
          hide-default-footer
          class="text-body-2"
        >
          <template #[`item.versionNumber`]="{ item }">
            <v-chip size="x-small" variant="tonal" color="primary">
              v{{ item.versionNumber }}
            </v-chip>
          </template>

          <template #[`item.uploadedAt`]="{ item }">
            {{
              item.uploadedAt
                ? toShortDateString(new Date(item.uploadedAt))
                : "—"
            }}
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
                  : 'Download not available — no GridFS record for this version'
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

        <div class="text-caption text-medium-emphasis mt-2 pb-1">
          Downloads retrieve the full file payload from storage — check the
          size column before downloading large files.
        </div>
      </v-expansion-panel-text>
    </v-expansion-panel>
  </v-expansion-panels>
</template>
