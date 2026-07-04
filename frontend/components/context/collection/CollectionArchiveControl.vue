<script setup lang="ts">
/**
 * #27-ARCHIVED — owner-or-instance-admin control for flipping a Collection's
 * publication-state to/from ARCHIVED.
 *
 * Renders:
 *   - "Archived" chip when status === "ARCHIVED" (informational, always shown)
 *   - "Archive collection" button when status !== "ARCHIVED" and isManager
 *   - "Unarchive" button when status === "ARCHIVED" and isManager
 *   - "Archived — read-only" tooltip via the chip's title
 *
 * Calls PATCH /v2/collections/{appId}/publication-state — the dedicated
 * endpoint that gates on Manage permission OR instance-admin role (per
 * the #27-ARCHIVED design in aidocs/16-dispatcher-backlog.md).
 */
import { computed, ref } from "vue";

const props = defineProps<{
  appId: string | null;
  status: string | null | undefined;
  isManager: boolean;
}>();

const emit = defineEmits<{
  (e: "changed", newState: string): void;
}>();

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}
const isSaving = ref(false);
const showConfirm = ref(false);
const pendingState = ref<string | null>(null);

const isArchived = computed(() => props.status === "ARCHIVED");

function askArchive() {
  pendingState.value = "ARCHIVED";
  showConfirm.value = true;
}

function askUnarchive() {
  pendingState.value = "READY";
  showConfirm.value = true;
}

async function applyFlip() {
  if (!props.appId || !pendingState.value) return;
  isSaving.value = true;
  try {
    const url = `${v2BaseUrl()}/v2/collections/${encodeURIComponent(props.appId)}/publication-state`;
    const resp = await fetch(url, {
      method: "PATCH",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ state: pendingState.value }),
    });
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
    emit("changed", pendingState.value);
    showConfirm.value = false;
  } finally {
    isSaving.value = false;
  }
}
</script>

<template>
  <div class="d-inline-flex align-center ga-2">
    <v-chip
      v-if="isArchived"
      color="secondary"
      size="small"
      variant="tonal"
      label
      prepend-icon="mdi-archive-lock"
      title="This Collection is ARCHIVED — frozen, prune-only. New writes to its children return 409."
      data-test="archived-chip"
    >
      Archived
    </v-chip>

    <v-btn
      v-if="isManager && !isArchived && appId"
      variant="text"
      density="comfortable"
      size="small"
      prepend-icon="mdi-archive-arrow-down-outline"
      :loading="isSaving"
      data-test="archive-collection-btn"
      @click="askArchive"
    >
      Archive collection
    </v-btn>

    <v-btn
      v-if="isManager && isArchived && appId"
      variant="text"
      density="comfortable"
      size="small"
      prepend-icon="mdi-archive-arrow-up-outline"
      :loading="isSaving"
      data-test="unarchive-collection-btn"
      @click="askUnarchive"
    >
      Unarchive
    </v-btn>

    <v-dialog v-model="showConfirm" max-width="480">
      <v-card>
        <v-card-title>
          {{ pendingState === "ARCHIVED" ? "Archive collection?" : "Unarchive collection?" }}
        </v-card-title>
        <v-card-text>
          <template v-if="pendingState === 'ARCHIVED'">
            Archiving freezes this Collection. New DataObjects, references, and
            child writes will return <code>409 Conflict</code> until you unarchive.
            Reads continue to work.
          </template>
          <template v-else>
            Unarchive sets the publication state to READY. New writes will be
            accepted again.
          </template>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="showConfirm = false">Cancel</v-btn>
          <v-btn
            variant="flat"
            :color="pendingState === 'ARCHIVED' ? 'secondary' : 'primary'"
            :loading="isSaving"
            data-test="confirm-archive-btn"
            @click="applyFlip"
          >
            {{ pendingState === "ARCHIVED" ? "Archive" : "Unarchive" }}
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>
