<script setup lang="ts">
/**
 * UI1a — Snapshots panel for the Collection detail page.
 *
 * Allows owners/managers to:
 * - Create a snapshot (name + optional description)
 * - List snapshots for the collection
 * - Delete a snapshot (with confirmation dialog)
 * - Diff two snapshots (visual diff showing added/removed/changed entities)
 *
 * Backed by V2b/V2e endpoints via useSnapshots composable.
 */

import type { SnapshotDiffIO, SnapshotIO } from "@dlr-shepard/backend-client";

const props = defineProps<{
  collectionAppId: string;
}>();

const collectionAppIdRef = computed(() => props.collectionAppId);

const { snapshots, isLoading, isSaving, createSnapshot, deleteSnapshot, diffSnapshots } =
  useSnapshots(collectionAppIdRef);

// ── Create form ──────────────────────────────────────────────────────────────

const showCreateForm = ref(false);
const newName = ref("");
const newDescription = ref("");
const createFormRef = ref<{ validate: () => Promise<{ valid: boolean }> } | null>(null);

async function submitCreate() {
  if (!createFormRef.value) return;
  const { valid } = await createFormRef.value.validate();
  if (!valid) return;

  const result = await createSnapshot(newName.value.trim(), newDescription.value.trim() || null);
  if (result) {
    newName.value = "";
    newDescription.value = "";
    showCreateForm.value = false;
  }
}

// ── Delete dialog ────────────────────────────────────────────────────────────

const showDeleteDialog = ref(false);
const snapshotToDelete = ref<SnapshotIO | null>(null);

function promptDelete(snapshot: SnapshotIO) {
  snapshotToDelete.value = snapshot;
  showDeleteDialog.value = true;
}

async function confirmDelete() {
  if (!snapshotToDelete.value?.appId) return;
  const ok = await deleteSnapshot(snapshotToDelete.value.appId);
  if (ok) {
    showDeleteDialog.value = false;
    snapshotToDelete.value = null;
  }
}

function cancelDelete() {
  showDeleteDialog.value = false;
  snapshotToDelete.value = null;
}

// ── Diff dialog ──────────────────────────────────────────────────────────────

const showDiffDialog = ref(false);
const diffBase = ref<SnapshotIO | null>(null);
const diffHead = ref<SnapshotIO | null>(null);
const diffResult = ref<SnapshotDiffIO | null>(null);
const isDiffLoading = ref(false);

/** Open the diff picker with snapshot A pre-selected as base. */
function openDiffPicker(snapshot: SnapshotIO) {
  diffBase.value = snapshot;
  diffHead.value = null;
  diffResult.value = null;
  showDiffDialog.value = true;
}

/** List of snapshots selectable as the "head" (all except the base). */
const diffHeadOptions = computed(() =>
  snapshots.value.filter(s => s.appId !== diffBase.value?.appId),
);

async function runDiff() {
  if (!diffBase.value?.appId || !diffHead.value?.appId) return;
  isDiffLoading.value = true;
  diffResult.value = null;
  diffResult.value = await diffSnapshots(diffBase.value.appId, diffHead.value.appId);
  isDiffLoading.value = false;
}

function closeDiff() {
  showDiffDialog.value = false;
  diffBase.value = null;
  diffHead.value = null;
  diffResult.value = null;
}

// ── Helpers ──────────────────────────────────────────────────────────────────

function formatDate(isoOrNull?: string | null): string {
  if (!isoOrNull) return "—";
  return new Date(isoOrNull).toLocaleString();
}

function snapshotLabel(s: SnapshotIO): string {
  return s.name + (s.snapshotCapturedAt ? ` (${formatDate(s.snapshotCapturedAt)})` : "");
}

const nameRules = [
  (v: string) => !!v?.trim() || "Name is required",
  (v: string) => v.length <= 80 || "Name must be 80 characters or fewer",
];
</script>

<template>
  <!-- Create section -->
  <div class="mb-4">
    <v-btn
      v-if="!showCreateForm"
      prepend-icon="mdi-camera-plus-outline"
      variant="tonal"
      color="primary"
      size="small"
      @click="showCreateForm = true"
    >
      New Snapshot
    </v-btn>

    <v-card v-else variant="outlined" class="pa-4">
      <div class="text-subtitle-2 mb-3">Create Snapshot</div>
      <v-form ref="createFormRef" @submit.prevent="submitCreate">
        <v-text-field
          v-model="newName"
          label="Name"
          placeholder="e.g. v1.0 — campaign close"
          :rules="nameRules"
          counter="80"
          maxlength="80"
          variant="outlined"
          density="compact"
          class="mb-2"
          required
        />
        <v-textarea
          v-model="newDescription"
          label="Description (optional)"
          variant="outlined"
          density="compact"
          rows="2"
          auto-grow
          class="mb-3"
        />
        <div class="d-flex ga-2">
          <v-btn
            type="submit"
            color="primary"
            variant="flat"
            size="small"
            :loading="isSaving"
            :disabled="isSaving"
          >
            Create snapshot
          </v-btn>
          <v-btn
            variant="text"
            size="small"
            :disabled="isSaving"
            @click="showCreateForm = false"
          >
            Cancel
          </v-btn>
        </div>
      </v-form>
    </v-card>
  </div>

  <!-- List section -->
  <div v-if="isLoading" class="d-flex justify-center py-4">
    <v-progress-circular indeterminate color="primary" size="28" />
  </div>

  <div v-else-if="snapshots.length === 0 && !showCreateForm" class="text-medium-emphasis text-body-2 py-2">
    No snapshots yet. Create one to capture the current state of this collection.
  </div>

  <v-list v-else-if="snapshots.length > 0" lines="two" class="pa-0">
    <v-list-item
      v-for="snap in snapshots"
      :key="snap.appId ?? snap.name"
      class="px-0"
    >
      <template #title>
        <span class="text-body-2 font-weight-medium">{{ snap.name }}</span>
      </template>
      <template #subtitle>
        <span class="text-caption text-medium-emphasis">
          {{ formatDate(snap.snapshotCapturedAt) }}
          <template v-if="snap.snapshotCreatedByUsername">
            · by {{ snap.snapshotCreatedByUsername }}
          </template>
          · {{ snap.entryCount ?? 0 }} entities
        </span>
        <span v-if="snap.description" class="d-block text-caption text-medium-emphasis mt-1">
          {{ snap.description }}
        </span>
      </template>
      <template #append>
        <div class="d-flex align-center ga-1">
          <v-btn
            variant="tonal"
            size="x-small"
            color="secondary"
            prepend-icon="mdi-compare"
            :disabled="snapshots.length < 2"
            @click="openDiffPicker(snap)"
          >
            Diff
          </v-btn>
          <v-btn
            variant="text"
            size="x-small"
            icon="mdi-delete-outline"
            color="error"
            :loading="isSaving"
            :aria-label="`Delete snapshot ${snap.name}`"
            @click="promptDelete(snap)"
          />
        </div>
      </template>
    </v-list-item>
  </v-list>

  <!-- Delete confirmation dialog -->
  <v-dialog
    v-model="showDeleteDialog"
    max-width="480"
    persistent
    @keydown.esc="cancelDelete"
  >
    <v-card color="canvas">
      <template #title>Delete snapshot?</template>
      <template #text>
        <p class="text-body-2">
          Are you sure you want to delete
          <strong>{{ snapshotToDelete?.name }}</strong>?
          This cannot be undone.
        </p>
      </template>
      <template #actions>
        <v-spacer />
        <v-btn variant="flat" color="treeview" @click="cancelDelete">Cancel</v-btn>
        <v-btn
          variant="flat"
          color="error"
          :loading="isSaving"
          class="ml-2"
          @click="confirmDelete"
        >
          Delete
        </v-btn>
      </template>
    </v-card>
  </v-dialog>

  <!-- Diff dialog -->
  <v-dialog
    v-model="showDiffDialog"
    max-width="700"
    @keydown.esc="closeDiff"
  >
    <v-card color="canvas">
      <template #title>
        <div class="d-flex justify-space-between align-center">
          <span>Snapshot Diff</span>
          <v-btn
            variant="plain"
            density="compact"
            icon="mdi-close"
            aria-label="Close diff dialog"
            @click="closeDiff"
          />
        </div>
      </template>

      <template #text>
        <div class="d-flex flex-column ga-4">
          <!-- Snapshot A (base — pre-selected) -->
          <v-text-field
            :model-value="snapshotLabel(diffBase!)"
            label="Base snapshot (A)"
            readonly
            variant="outlined"
            density="compact"
            prepend-inner-icon="mdi-alpha-a-circle-outline"
          />

          <!-- Snapshot B (head — choose) -->
          <v-select
            v-model="diffHead"
            :items="diffHeadOptions"
            :item-title="snapshotLabel"
            item-value="appId"
            return-object
            label="Compare with (B)"
            variant="outlined"
            density="compact"
            prepend-inner-icon="mdi-alpha-b-circle-outline"
            no-data-text="No other snapshots available"
          />

          <v-btn
            color="primary"
            variant="flat"
            :loading="isDiffLoading"
            :disabled="!diffHead || isDiffLoading"
            @click="runDiff"
          >
            Compare
          </v-btn>

          <!-- Diff results -->
          <template v-if="diffResult">
            <v-divider />

            <div class="d-flex flex-wrap ga-3 align-center">
              <v-chip color="success" variant="tonal" size="small" prepend-icon="mdi-plus">
                {{ diffResult.added.length }} added
              </v-chip>
              <v-chip color="error" variant="tonal" size="small" prepend-icon="mdi-minus">
                {{ diffResult.removed.length }} removed
              </v-chip>
              <v-chip color="warning" variant="tonal" size="small" prepend-icon="mdi-pencil">
                {{ diffResult.changed.length }} changed
              </v-chip>
              <v-chip variant="tonal" size="small" prepend-icon="mdi-check">
                {{ diffResult.unchangedCount }} unchanged
              </v-chip>
            </div>

            <!-- Added -->
            <div v-if="diffResult.added.length > 0">
              <div class="text-subtitle-2 text-success mb-1">Added</div>
              <v-chip-group column>
                <v-chip
                  v-for="appId in diffResult.added"
                  :key="appId"
                  color="success"
                  variant="outlined"
                  size="small"
                  label
                >
                  {{ appId }}
                </v-chip>
              </v-chip-group>
            </div>

            <!-- Removed -->
            <div v-if="diffResult.removed.length > 0">
              <div class="text-subtitle-2 text-error mb-1">Removed</div>
              <v-chip-group column>
                <v-chip
                  v-for="appId in diffResult.removed"
                  :key="appId"
                  color="error"
                  variant="outlined"
                  size="small"
                  label
                >
                  {{ appId }}
                </v-chip>
              </v-chip-group>
            </div>

            <!-- Changed -->
            <div v-if="diffResult.changed.length > 0">
              <div class="text-subtitle-2 text-warning mb-1">Changed</div>
              <v-list density="compact" class="pa-0">
                <v-list-item
                  v-for="entry in diffResult.changed"
                  :key="entry.entityAppId"
                  class="px-0"
                >
                  <template #title>
                    <span class="text-caption font-weight-medium">{{ entry.entityAppId }}</span>
                  </template>
                  <template #subtitle>
                    <span class="text-caption text-medium-emphasis">
                      rev {{ entry.revisionA }} → rev {{ entry.revisionB }}
                    </span>
                  </template>
                </v-list-item>
              </v-list>
            </div>
          </template>
        </div>
      </template>

      <template #actions>
        <v-spacer />
        <v-btn variant="flat" color="treeview" @click="closeDiff">Close</v-btn>
      </template>
    </v-card>
  </v-dialog>
</template>
