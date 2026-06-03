<script setup lang="ts">
/**
 * UI7 — Version history expansion panel for the FileReference detail page.
 *
 * Renders an ExpansionPanelItem (closed by default) that lists all payload
 * versions for a given file in a FileContainer. Each row shows version
 * number, truncated SHA-256, human-readable size, upload timestamp, uploader,
 * and a per-version download button.
 *
 * Props:
 *   containerAppId — UUID v7 of the FileContainer (from referencedContainerAppId)
 *   containerId    — numeric Neo4j id of the FileContainer (for the v1 download endpoint)
 *   fileName       — original name of the file (path param for the versions endpoint)
 *
 * Lazy: versions are fetched only when the panel is expanded.
 */

import { FileContainerApi } from "@dlr-shepard/backend-client";
import {
  useFetchPayloadVersions,
  type PayloadVersionIO,
} from "~/composables/container/useFetchPayloadVersions";
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

// Track the open/closed state of the panel so we can lazy-load on first open.
const isOpen = ref(false);
const hasLoaded = ref(false);

function onToggle(open: boolean) {
  isOpen.value = open;
  if (open && !hasLoaded.value) {
    hasLoaded.value = true;
    void load();
  }
}

const headers = [
  { title: "Version",  key: "versionNumber", width: "88px",  sortable: false },
  { title: "Uploaded", key: "uploadedAt",     sortable: false },
  { title: "By",       key: "uploadedBy",     sortable: false },
  { title: "Size",     key: "sizeBytes",       sortable: false },
  { title: "SHA-256",  key: "sha256",          sortable: false },
  { title: "",         key: "actions",         sortable: false, width: "60px" },
];

// ── Download ────────────────────────────────────────────────────────────────

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

// ── Formatting helpers ───────────────────────────────────────────────────────

function fmtBytes(b: number | null): string {
  if (b === null || b === undefined) return "—";
  if (b === 0) return "0 B";
  if (b < 1_048_576) return `${(b / 1_024).toFixed(1)} KB`;
  if (b < 1_073_741_824) return `${(b / 1_048_576).toFixed(1)} MB`;
  return `${(b / 1_073_741_824).toFixed(2)} GB`;
}

function sha256Short(hash: string | null): string {
  if (!hash) return "—";
  return hash.slice(0, 12) + "…";
}
</script>

<template>
  <!-- data-testid referenced by UI7 Vitest tests -->
  <ExpansionPanels
    data-testid="payload-version-history-panel"
    :default-open="[]"
  >
    <v-expansion-panel
      class="pb-2 bg-canvas"
      @update:model-value="(v: unknown) => onToggle(!!v)"
    >
      <v-expansion-panel-title
        v-slot="slotProps"
        min-height="32"
        class="px-0 py-0"
        hide-actions
      >
        <v-icon
          :icon="slotProps.expanded ? 'mdi-chevron-down' : 'mdi-chevron-right'"
        />
        <div class="text-h5 text-textbody1 pl-2">Version history</div>
        <div
          v-if="!isLoading && versions.length > 0"
          class="text-h5 text-low-emphasis pl-2"
        >
          ({{ versions.length }})
        </div>
        <v-spacer />
      </v-expansion-panel-title>

      <v-expansion-panel-text class="no-padding-bottom">
        <!-- Loading state -->
        <div
          v-if="isLoading"
          role="status"
          class="d-flex align-center ga-2 text-medium-emphasis text-body-2 pa-3"
        >
          <v-progress-circular indeterminate size="16" width="2" />
          Loading version history…
        </div>

        <!-- Error state -->
        <div
          v-else-if="error"
          class="text-error text-body-2 pa-3"
        >
          {{ error }}
        </div>

        <!-- Empty state -->
        <div
          v-else-if="versions.length === 0"
          class="text-medium-emphasis text-body-2 pa-3"
        >
          No version history found for this file. Versions are recorded from
          first upload onwards; files uploaded before PV1a shipped will not
          have history entries.
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
                  : 'Download unavailable — uploaded via presigned URL'
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
      </v-expansion-panel-text>
    </v-expansion-panel>
  </ExpansionPanels>
</template>

<style scoped>
.no-padding-bottom :deep(.v-expansion-panel-text__wrapper) {
  padding-bottom: 0;
  padding-top: 12px;
  padding-left: 30.5px;
}
.text-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 0.85em;
}
</style>
