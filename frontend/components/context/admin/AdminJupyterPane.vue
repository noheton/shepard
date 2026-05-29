<script setup lang="ts">
import { AdminFragments } from "./adminMenuItems";
import type { JupyterConfigPatch } from "~/composables/context/admin/useJupyterConfig";
import { useJupyterConfig } from "~/composables/context/admin/useJupyterConfig";

// J1e — instance-wide JupyterHub link-out configuration.
// adminMode=true: reads from /v2/admin/jupyter/config (instance-admin
// gated). The pane is only rendered behind isInstanceAdmin so the role
// check is doubly enforced.
const { config, isLoading, isSaving, error, refresh, patch } = useJupyterConfig({
  adminMode: true,
});

// ─── Edit dialog state ───────────────────────────────────────────────────
const dialogOpen = ref(false);
const editEnabled = ref(false);
const editHubUrl = ref("");
const saveError = ref<string | null>(null);

// Inline URL validation — must be empty (blank = revert to default) or
// an absolute http(s) URL. The backend rejects anything else with HTTP
// 400; inline validation gives the operator immediate feedback.
const HUB_URL_RE = /^https?:\/\/[^\s/$.?#].[^\s]*$/i;
const hubUrlError = computed(() => {
  const val = editHubUrl.value.trim();
  if (val === "") return null;
  if (!HUB_URL_RE.test(val)) {
    return "Must be an absolute http(s) URL (e.g. https://hub.example.org), or leave blank to revert to the deploy-time default.";
  }
  return null;
});

const canSave = computed(() => hubUrlError.value === null);

function openEdit() {
  editEnabled.value = config.value?.enabled ?? false;
  editHubUrl.value = config.value?.hubUrl ?? "";
  saveError.value = null;
  dialogOpen.value = true;
}

function cancelEdit() {
  dialogOpen.value = false;
  saveError.value = null;
}

async function save() {
  if (!canSave.value) return;
  saveError.value = null;

  const updates: JupyterConfigPatch = {
    enabled: editEnabled.value,
  };
  const rawUrl = editHubUrl.value.trim();
  // Empty string → null = revert to deploy-time default.
  updates.hubUrl = rawUrl === "" ? null : rawUrl;

  const result = await patch(updates);
  if (result) {
    dialogOpen.value = false;
  } else {
    saveError.value = error.value ?? "Failed to save. Please try again.";
  }
}

const affordanceVisible = computed(
  () =>
    !!config.value &&
    config.value.enabled === true &&
    !!config.value.hubUrl &&
    config.value.hubUrl.length > 0,
);
</script>

<template>
  <div :id="AdminFragments.JUPYTER" class="d-flex flex-column ga-4">
    <!-- Header row -->
    <div class="d-flex align-center justify-space-between flex-wrap ga-2">
      <div class="d-flex align-center ga-3">
        <h4 class="text-h4">JupyterHub link-out</h4>
        <v-btn
          icon="mdi-refresh"
          variant="text"
          size="small"
          :loading="isLoading"
          @click="refresh"
        />
      </div>
      <v-btn
        variant="tonal"
        color="primary"
        prepend-icon="mdi-pencil-outline"
        :disabled="isLoading"
        data-testid="jupyter-edit-btn"
        @click="openEdit"
      >
        Edit
      </v-btn>
    </div>

    <p class="text-medium-emphasis">
      Controls the per-notebook
      <strong>Open in JupyterHub</strong> action on rows in the unified Data
      References table. The action is visible to users only when both
      <code>enabled</code> is true AND <code>hubUrl</code> is set. Mutations
      are recorded as <code>:Activity</code> rows via PROV1a.
    </p>

    <CenteredLoadingSpinner v-if="isLoading && !config" />

    <v-card v-else-if="config" variant="outlined" data-testid="jupyter-config-card">
      <v-list>
        <v-list-item>
          <template #prepend>
            <v-icon icon="mdi-toggle-switch-outline" />
          </template>
          <v-list-item-title>Enabled</v-list-item-title>
          <v-list-item-subtitle>
            <v-chip
              :color="config.enabled ? 'success' : 'default'"
              size="small"
              variant="tonal"
              data-testid="jupyter-enabled-chip"
            >
              {{ config.enabled ? "yes" : "no" }}
            </v-chip>
          </v-list-item-subtitle>
        </v-list-item>
        <v-divider />
        <v-list-item>
          <template #prepend>
            <v-icon icon="mdi-link-variant" />
          </template>
          <v-list-item-title>Hub URL</v-list-item-title>
          <v-list-item-subtitle>
            <span
              v-if="config.hubUrl"
              class="text-mono"
              data-testid="jupyter-hub-url-value"
            >{{ config.hubUrl }}</span>
            <span v-else class="text-medium-emphasis" data-testid="jupyter-hub-url-empty">(not set)</span>
          </v-list-item-subtitle>
        </v-list-item>
        <v-divider />
        <v-list-item>
          <template #prepend>
            <v-icon icon="mdi-eye-outline" />
          </template>
          <v-list-item-title>Affordance visible to users</v-list-item-title>
          <v-list-item-subtitle>
            <v-chip
              :color="affordanceVisible ? 'success' : 'warning'"
              size="small"
              variant="tonal"
              data-testid="jupyter-affordance-chip"
            >
              {{ affordanceVisible ? "yes" : "no" }}
            </v-chip>
            <span v-if="!affordanceVisible" class="text-medium-emphasis ml-2">
              Set both <code>enabled</code> and a valid <code>hubUrl</code> to surface the action.
            </span>
          </v-list-item-subtitle>
        </v-list-item>
      </v-list>
    </v-card>

    <v-alert v-if="error" type="error" variant="tonal" closable>
      {{ error }}
    </v-alert>

    <!-- Edit dialog -->
    <v-dialog v-model="dialogOpen" max-width="540" persistent>
      <v-card>
        <v-card-title>Edit JupyterHub config</v-card-title>
        <v-card-text class="d-flex flex-column ga-4">
          <v-switch
            v-model="editEnabled"
            label="Enabled (master switch for the link-out affordance)"
            color="primary"
            hide-details
            data-testid="jupyter-edit-enabled"
          />
          <v-text-field
            v-model="editHubUrl"
            label="JupyterHub base URL"
            placeholder="https://hub.example.org"
            hint="Absolute http(s) URL. Leave blank to revert to the deploy-time default (shepard.jupyter.hub-url)."
            persistent-hint
            density="comfortable"
            variant="outlined"
            :error-messages="hubUrlError ?? undefined"
            data-testid="jupyter-edit-hub-url"
          />
          <v-alert v-if="saveError" type="error" variant="tonal" data-testid="jupyter-save-error">
            {{ saveError }}
          </v-alert>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" :disabled="isSaving" @click="cancelEdit">Cancel</v-btn>
          <v-btn
            color="primary"
            variant="flat"
            :loading="isSaving"
            :disabled="!canSave"
            data-testid="jupyter-save-btn"
            @click="save"
          >Save</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<style scoped>
.text-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, "Liberation Mono", monospace;
  font-size: 0.9rem;
}
</style>
