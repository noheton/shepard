<script setup lang="ts">
import { AdminFragments } from "./adminMenuItems";
import { useFileMigration } from "~/composables/context/admin/useFileMigration";
import type { StorageAdapterIO } from "~/composables/context/admin/useFileMigration";

const {
  storageStatus,
  isLoadingStorage,
  storageError,
  refreshStorage,
  migrationState,
  isLoadingState,
  migrationError,
  refreshMigrationState,
  isTriggering,
  triggerError,
  triggerSuccess,
  triggerMigration,
  isRollingBack,
  rollbackError,
  rollbackSuccess,
  rollbackFile,
} = useFileMigration();

// ─── Trigger form ─────────────────────────────────────────────────────────────

const sourceId = ref<string | null>(null);
const targetId = ref<string | null>(null);
const confirmDialogOpen = ref(false);

const adapterItems = computed<{ title: string; value: string }[]>(() =>
  (storageStatus.value?.adapters ?? []).map((a: StorageAdapterIO) => ({
    title: adapterLabel(a),
    value: a.id,
  })),
);

const canTrigger = computed(() => {
  if (!sourceId.value || !targetId.value) return false;
  if (sourceId.value === targetId.value) return false;
  if (migrationState.value?.status === "RUNNING") return false;
  return true;
});

function adapterLabel(a: StorageAdapterIO): string {
  const badges: string[] = [];
  if (a.active) badges.push("active");
  if (!a.enabled) badges.push("disabled");
  return badges.length > 0 ? `${a.id} (${badges.join(", ")})` : a.id;
}

function openConfirm() {
  if (!canTrigger.value) return;
  confirmDialogOpen.value = true;
}

async function confirmTrigger() {
  confirmDialogOpen.value = false;
  await triggerMigration(sourceId.value!, targetId.value!);
}

// ─── Rollback form ────────────────────────────────────────────────────────────

const rollbackAppId = ref("");
const rollbackDialogOpen = ref(false);

function openRollbackDialog() {
  rollbackError.value = null;
  rollbackSuccess.value = null;
  rollbackAppId.value = "";
  rollbackDialogOpen.value = true;
}

async function confirmRollback() {
  if (!rollbackAppId.value.trim()) return;
  const ok = await rollbackFile(rollbackAppId.value.trim());
  if (ok) rollbackDialogOpen.value = false;
}

// ─── Progress helpers ─────────────────────────────────────────────────────────

const progressPercent = computed(() => {
  const s = migrationState.value;
  if (!s || s.filesTotal === 0) return 0;
  return Math.round((s.filesMigrated / s.filesTotal) * 100);
});

const statusColor = computed(() => {
  switch (migrationState.value?.status) {
    case "RUNNING": return "primary";
    case "DONE": return "success";
    case "FAILED": return "error";
    default: return "default";
  }
});

function fmtInstant(iso: string | null | undefined): string {
  if (!iso) return "—";
  try {
    return new Date(iso).toLocaleString();
  } catch {
    return iso;
  }
}

function elapsedSeconds(
  startedAt: string | null,
  updatedAt: string | null,
): string {
  if (!startedAt) return "—";
  const end = updatedAt ? new Date(updatedAt) : new Date();
  const start = new Date(startedAt);
  const secs = Math.round((end.getTime() - start.getTime()) / 1000);
  if (secs < 60) return `${secs}s`;
  const mins = Math.floor(secs / 60);
  const rem = secs % 60;
  return rem > 0 ? `${mins}m ${rem}s` : `${mins}m`;
}

async function handleRefreshAll() {
  await Promise.all([refreshStorage(), refreshMigrationState()]);
}
</script>

<template>
  <div :id="AdminFragments.FILE_MIGRATION" class="d-flex flex-column ga-4">
    <!-- Header -->
    <div class="d-flex align-center justify-space-between flex-wrap ga-2">
      <div class="d-flex align-center ga-3">
        <h4 class="text-h4">File Storage Migration</h4>
        <v-btn
          icon="mdi-refresh"
          variant="text"
          size="small"
          :loading="isLoadingStorage || isLoadingState"
          @click="handleRefreshAll"
        />
      </div>
    </div>

    <p class="text-body-2 text-medium-emphasis">
      Move file payloads between storage backends (e.g. GridFS → Garage S3).
      Migration runs in the background — the page polls for progress every 2 s.
      Re-running after a partial failure is safe: already-migrated files are skipped.
      OIDs are preserved so existing API clients keep working.
    </p>

    <!-- Error banners -->
    <v-alert
      v-if="storageError"
      type="error"
      variant="tonal"
      closable
      @click:close="storageError = null"
    >
      {{ storageError }}
    </v-alert>
    <v-alert
      v-if="migrationError"
      type="error"
      variant="tonal"
      closable
      @click:close="migrationError = null"
    >
      {{ migrationError }}
    </v-alert>

    <v-progress-linear
      v-if="(isLoadingStorage || isLoadingState) && !storageStatus && !migrationState"
      indeterminate
    />

    <!-- ── Section 1: Storage Adapters ────────────────────────────────────── -->
    <v-card v-if="storageStatus" variant="outlined">
      <v-card-title class="d-flex align-center ga-2 pt-4 pb-2">
        <v-icon color="primary">mdi-database-cog-outline</v-icon>
        Storage Adapters
      </v-card-title>
      <v-card-text>
        <v-row dense>
          <v-col
            v-for="adapter in storageStatus.adapters"
            :key="adapter.id"
            cols="12"
            sm="6"
            md="4"
          >
            <v-card
              variant="tonal"
              :color="adapter.active ? 'primary' : adapter.enabled ? undefined : 'warning'"
              density="compact"
              class="pa-2"
            >
              <div class="d-flex align-center justify-space-between">
                <div class="text-body-2 font-weight-medium">
                  {{ adapter.id }}
                </div>
                <div class="d-flex ga-1">
                  <v-chip
                    v-if="adapter.active"
                    size="x-small"
                    color="primary"
                    variant="tonal"
                  >
                    active
                  </v-chip>
                  <v-chip
                    size="x-small"
                    :color="adapter.enabled ? 'success' : 'warning'"
                    variant="tonal"
                  >
                    {{ adapter.enabled ? "enabled" : "disabled" }}
                  </v-chip>
                </div>
              </div>
            </v-card>
          </v-col>
        </v-row>
        <div
          v-if="storageStatus.adapters.length === 0"
          class="text-body-2 text-medium-emphasis"
        >
          No storage adapters discovered.
        </div>
      </v-card-text>
    </v-card>

    <!-- ── Section 2: Trigger Migration ──────────────────────────────────── -->
    <v-card variant="outlined">
      <v-card-title class="d-flex align-center ga-2 pt-4 pb-2">
        <v-icon color="warning">mdi-database-arrow-right-outline</v-icon>
        Trigger Migration
      </v-card-title>
      <v-card-text class="d-flex flex-column ga-4">
        <v-alert
          v-if="migrationState?.status === 'RUNNING'"
          type="info"
          variant="tonal"
          density="compact"
        >
          A migration is currently running. Wait for it to complete before
          starting a new one.
        </v-alert>

        <v-alert v-if="triggerError" type="error" variant="tonal" closable @click:close="triggerError = null">
          {{ triggerError }}
        </v-alert>
        <v-alert v-if="triggerSuccess && migrationState?.status !== 'IDLE'" type="success" variant="tonal" density="compact">
          Migration job triggered. Progress is shown below.
        </v-alert>

        <v-row dense>
          <v-col cols="12" sm="5">
            <v-select
              v-model="sourceId"
              :items="adapterItems"
              label="Source adapter (drain from)"
              variant="outlined"
              density="comfortable"
              :disabled="migrationState?.status === 'RUNNING' || isTriggering"
              clearable
            />
          </v-col>
          <v-col cols="12" sm="2" class="d-flex align-center justify-center">
            <v-icon size="large" color="medium-emphasis">mdi-arrow-right</v-icon>
          </v-col>
          <v-col cols="12" sm="5">
            <v-select
              v-model="targetId"
              :items="adapterItems"
              label="Target adapter (write to)"
              variant="outlined"
              density="comfortable"
              :disabled="migrationState?.status === 'RUNNING' || isTriggering"
              clearable
            />
          </v-col>
        </v-row>

        <div
          v-if="sourceId && targetId && sourceId === targetId"
          class="text-caption text-error"
        >
          Source and target must be different adapters.
        </div>
      </v-card-text>
      <v-card-actions class="pa-4 pt-0">
        <v-spacer />
        <v-btn
          color="warning"
          variant="tonal"
          prepend-icon="mdi-database-arrow-right-outline"
          :disabled="!canTrigger"
          :loading="isTriggering"
          @click="openConfirm"
        >
          Start migration
        </v-btn>
      </v-card-actions>
    </v-card>

    <!-- ── Section 3: Migration Status ───────────────────────────────────── -->
    <v-card v-if="migrationState" variant="outlined">
      <v-card-title class="d-flex align-center ga-2 pt-4 pb-2">
        <v-icon :color="statusColor">mdi-progress-check</v-icon>
        <span>Migration Status</span>
        <v-chip :color="statusColor" size="small" variant="tonal" class="ml-2">
          {{ migrationState.status }}
        </v-chip>
        <v-progress-circular
          v-if="migrationState.status === 'RUNNING'"
          indeterminate
          size="18"
          width="2"
          class="ml-2"
          aria-label="Migration in progress"
        />
      </v-card-title>
      <v-card-text class="d-flex flex-column ga-3">
        <template v-if="migrationState.status === 'IDLE'">
          <div class="text-body-2 text-medium-emphasis">
            No migration has been triggered since the last backend restart.
            State is in-memory only — a restart resets to IDLE.
          </div>
        </template>

        <template v-else>
          <!-- Progress bar -->
          <div v-if="migrationState.status === 'RUNNING' || migrationState.status === 'DONE'">
            <div class="d-flex justify-space-between text-caption text-medium-emphasis mb-1">
              <span>
                {{ migrationState.filesMigrated.toLocaleString() }} /
                {{ migrationState.filesTotal.toLocaleString() }} files migrated
                <template v-if="migrationState.filesFailed > 0">
                  <span class="text-error ml-2">
                    ({{ migrationState.filesFailed.toLocaleString() }} failed)
                  </span>
                </template>
              </span>
              <span>{{ progressPercent }}%</span>
            </div>
            <v-progress-linear
              :model-value="progressPercent"
              :color="migrationState.filesFailed > 0 ? 'warning' : statusColor"
              rounded
              height="8"
            />
          </div>

          <!-- Error message -->
          <v-alert
            v-if="migrationState.status === 'FAILED' && migrationState.errorMessage"
            type="error"
            variant="tonal"
            density="compact"
          >
            {{ migrationState.errorMessage }}
          </v-alert>

          <!-- Metadata row -->
          <v-row dense>
            <v-col cols="12" sm="6" md="3">
              <div class="text-caption text-medium-emphasis">Source</div>
              <div class="text-body-2 font-weight-medium">
                {{ migrationState.sourceProviderId ?? "—" }}
              </div>
            </v-col>
            <v-col cols="12" sm="6" md="3">
              <div class="text-caption text-medium-emphasis">Target</div>
              <div class="text-body-2 font-weight-medium">
                {{ migrationState.targetProviderId ?? "—" }}
              </div>
            </v-col>
            <v-col cols="12" sm="6" md="3">
              <div class="text-caption text-medium-emphasis">Started</div>
              <div class="text-body-2">{{ fmtInstant(migrationState.startedAt) }}</div>
            </v-col>
            <v-col cols="12" sm="6" md="3">
              <div class="text-caption text-medium-emphasis">
                {{ migrationState.status === "RUNNING" ? "Elapsed" : "Duration" }}
              </div>
              <div class="text-body-2">
                {{ elapsedSeconds(migrationState.startedAt, migrationState.updatedAt) }}
              </div>
            </v-col>
          </v-row>

          <div class="text-caption text-medium-emphasis">
            Last updated: {{ fmtInstant(migrationState.updatedAt) }}
            <template v-if="migrationState.status === 'RUNNING'">
              &mdash; polling every 2 s
            </template>
          </div>
        </template>
      </v-card-text>
    </v-card>

    <!-- ── Section 4: Per-file rollback (FS1e3) ──────────────────────────── -->
    <v-card variant="outlined">
      <v-card-title class="d-flex align-center ga-2 pt-4 pb-2">
        <v-icon color="default">mdi-backup-restore</v-icon>
        Per-file Rollback (FS1e3)
      </v-card-title>
      <v-card-text>
        <p class="text-body-2 text-medium-emphasis mb-3">
          Roll back a single file: re-writes its bytes from the current adapter
          back to the previous adapter and restores the original
          <code>providerId</code>. Refuses if the file was never migrated
          (returns 409). Does <em>not</em> delete bytes from the current adapter.
        </p>
        <v-alert
          v-if="rollbackSuccess"
          type="success"
          variant="tonal"
          density="compact"
          closable
          class="mb-2"
          @click:close="rollbackSuccess = null"
        >
          Rolled back file <code>{{ rollbackSuccess }}</code> successfully.
        </v-alert>
      </v-card-text>
      <v-card-actions class="pa-4 pt-0">
        <v-btn
          variant="outlined"
          prepend-icon="mdi-backup-restore"
          @click="openRollbackDialog"
        >
          Roll back a file…
        </v-btn>
      </v-card-actions>
    </v-card>

    <!-- ── Confirm trigger dialog ─────────────────────────────────────────── -->
    <v-dialog v-model="confirmDialogOpen" max-width="500">
      <v-card>
        <v-card-title class="text-h6 pa-4 d-flex align-center ga-2">
          <v-icon icon="mdi-database-arrow-right-outline" color="warning" />
          Confirm Migration
        </v-card-title>
        <v-card-text class="pa-4">
          <p class="text-body-2 mb-2">
            Migrate <strong>all files</strong> from
            <code>{{ sourceId }}</code> to <code>{{ targetId }}</code>?
          </p>
          <v-alert type="warning" variant="tonal" density="compact">
            This operation runs in the background and cannot be paused.
            It is safe to re-run after failure — already-migrated files are
            skipped. OIDs are preserved.
          </v-alert>
        </v-card-text>
        <v-card-actions class="pa-4 pt-0">
          <v-spacer />
          <v-btn variant="text" @click="confirmDialogOpen = false">Cancel</v-btn>
          <v-btn
            color="warning"
            variant="tonal"
            :loading="isTriggering"
            @click="confirmTrigger"
          >
            Start migration
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- ── Rollback dialog ────────────────────────────────────────────────── -->
    <v-dialog v-model="rollbackDialogOpen" max-width="500">
      <v-card>
        <v-card-title class="text-h6 pa-4 d-flex align-center ga-2">
          <v-icon icon="mdi-backup-restore" />
          Roll Back a File
        </v-card-title>
        <v-card-text class="pa-4 d-flex flex-column ga-3">
          <v-alert v-if="rollbackError" type="error" variant="tonal" density="compact">
            {{ rollbackError }}
          </v-alert>
          <v-text-field
            v-model="rollbackAppId"
            label="File appId (UUID v7)"
            placeholder="01942d…"
            variant="outlined"
            density="comfortable"
            hint="The :ShepardFile.appId to roll back. Find it via the file reference API or Neo4j."
            persistent-hint
          />
        </v-card-text>
        <v-card-actions class="pa-4 pt-0">
          <v-spacer />
          <v-btn variant="text" :disabled="isRollingBack" @click="rollbackDialogOpen = false">
            Cancel
          </v-btn>
          <v-btn
            color="primary"
            variant="tonal"
            :loading="isRollingBack"
            :disabled="!rollbackAppId.trim()"
            @click="confirmRollback"
          >
            Roll back
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>
