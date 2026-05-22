<script setup lang="ts">
/**
 * Task #135 — shared progress UI for both `FileUploadDialog` and
 * `DataObjectFileUploadDialog`.  Renders:
 *   - aggregate progress bar (or indeterminate if any file has unknown size)
 *   - aggregate stats: percent, bytes, ETA, files done / total
 *   - per-file status rows so a researcher sees which file is in-flight
 *   - Cancel button — emits `cancel`; dialog wires this to abort the batch
 */
import type {
  AggregateProgress,
  FileUploadState,
} from "~/composables/container/useFileUploadProgress";
import {
  formatBytes,
  formatEta,
} from "~/composables/container/useFileUploadProgress";

interface Props {
  items: FileUploadState[];
  aggregate: AggregateProgress;
  /** Show the Cancel button only while at least one file is still active. */
  canCancel: boolean;
}

defineProps<Props>();
defineEmits<{ (e: "cancel"): void }>();

function statusColor(status: FileUploadState["status"]): string {
  switch (status) {
    case "done":
      return "success";
    case "error":
      return "error";
    case "cancelled":
      return "warning";
    case "indeterminate":
    case "uploading":
      return "primary";
    default:
      return "secondary";
  }
}

function statusIcon(status: FileUploadState["status"]): string {
  switch (status) {
    case "done":
      return "mdi-check-circle";
    case "error":
      return "mdi-alert-circle";
    case "cancelled":
      return "mdi-cancel";
    case "indeterminate":
      return "mdi-progress-clock";
    case "uploading":
      return "mdi-upload";
    default:
      return "mdi-clock-outline";
  }
}
</script>

<template>
  <div
    class="d-flex flex-column ga-3 pa-3 rounded"
    style="background-color: rgb(var(--v-theme-canvas))"
    data-testid="upload-progress-panel"
  >
    <!-- Aggregate -->
    <div class="d-flex flex-column ga-1">
      <div class="d-flex align-center justify-space-between">
        <span class="text-subtitle-2 text-textbody">
          {{
            aggregate.filesTotal > 1
              ? `Uploading ${aggregate.filesDone} / ${aggregate.filesTotal} files`
              : "Uploading"
          }}
          <template v-if="aggregate.currentFilename">
            —
            <span
              class="text-medium-emphasis"
              data-testid="upload-progress-current-filename"
            >{{ aggregate.currentFilename }}</span>
          </template>
        </span>
        <v-btn
          v-if="canCancel"
          color="error"
          variant="outlined"
          size="small"
          density="comfortable"
          prepend-icon="mdi-cancel"
          data-testid="upload-progress-cancel"
          @click="$emit('cancel')"
        >
          Cancel
        </v-btn>
      </div>

      <v-progress-linear
        :model-value="aggregate.percent"
        :indeterminate="!aggregate.determinate && aggregate.filesDone < aggregate.filesTotal"
        color="primary"
        height="10"
        rounded
        data-testid="upload-progress-bar"
      />

      <div class="d-flex justify-space-between text-caption text-medium-emphasis">
        <span data-testid="upload-progress-bytes">
          {{ formatBytes(aggregate.uploadedBytes) }} /
          {{ formatBytes(aggregate.totalBytes) }}
        </span>
        <span data-testid="upload-progress-percent">
          {{ aggregate.determinate ? `${aggregate.percent.toFixed(0)}%` : "—" }}
        </span>
        <span data-testid="upload-progress-eta">
          <template v-if="aggregate.etaSeconds !== null">
            ETA {{ formatEta(aggregate.etaSeconds) }}
          </template>
          <template v-else>
            elapsed {{ formatEta(aggregate.elapsedSeconds) }}
          </template>
        </span>
      </div>
    </div>

    <!-- Per-file rows (only when more than one file in the batch) -->
    <div v-if="items.length > 1" class="d-flex flex-column ga-1">
      <div
        v-for="it in items"
        :key="it.index"
        class="d-flex align-center ga-2 text-caption"
        data-testid="upload-progress-file-row"
      >
        <v-icon
          :icon="statusIcon(it.status)"
          :color="statusColor(it.status)"
          size="small"
        />
        <span class="flex-grow-1 text-truncate" :title="it.name">{{ it.name }}</span>
        <span class="text-medium-emphasis">
          {{ formatBytes(it.bytesUploaded) }} / {{ formatBytes(it.size) }}
        </span>
        <span class="text-medium-emphasis" style="min-width: 36px; text-align: right">
          {{
            it.bytesTotal && it.bytesTotal > 0
              ? `${it.percent.toFixed(0)}%`
              : it.status === "done"
                ? "100%"
                : "—"
          }}
        </span>
      </div>
    </div>
  </div>
</template>
