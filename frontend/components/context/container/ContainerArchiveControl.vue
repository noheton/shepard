<script setup lang="ts">
/**
 * #27-ARCHIVED — owner-or-instance-admin control for flipping a Container's
 * publication-state to/from ARCHIVED. Works for any container kind (File,
 * Timeseries, StructuredData, Hdf) because the backend dispatches on the
 * container appId alone.
 *
 * Calls PATCH /v2/containers/{appId}/publication-state.
 */
import { computed, ref } from "vue";

const props = defineProps<{
  appId: string | null;
  status: string | null | undefined;
  isManager: boolean;
  containerKindLabel?: string;
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
const kindLabel = computed(() => props.containerKindLabel ?? "Container");

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
    const url = `${v2BaseUrl()}/v2/containers/${encodeURIComponent(props.appId)}/publication-state`;
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
      :title="`This ${kindLabel} is ARCHIVED — frozen, prune-only. New payload writes return 409.`"
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
      data-test="archive-container-btn"
      @click="askArchive"
    >
      Archive {{ kindLabel.toLowerCase() }}
    </v-btn>

    <v-btn
      v-if="isManager && isArchived && appId"
      variant="text"
      density="comfortable"
      size="small"
      prepend-icon="mdi-archive-arrow-up-outline"
      :loading="isSaving"
      data-test="unarchive-container-btn"
      @click="askUnarchive"
    >
      Unarchive
    </v-btn>

    <v-dialog v-model="showConfirm" max-width="480">
      <v-card>
        <v-card-title>
          {{ pendingState === "ARCHIVED" ? `Archive ${kindLabel.toLowerCase()}?` : `Unarchive ${kindLabel.toLowerCase()}?` }}
        </v-card-title>
        <v-card-text>
          <template v-if="pendingState === 'ARCHIVED'">
            Archiving freezes this {{ kindLabel.toLowerCase() }}. New payload
            writes (POST/PATCH/DELETE) will return <code>409 Conflict</code>
            until you unarchive. Reads continue to work.
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
