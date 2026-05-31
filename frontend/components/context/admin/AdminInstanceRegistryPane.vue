<script setup lang="ts">
/**
 * AdminInstanceRegistryPane (PLACEHOLDER-REPLACE-FE-PROV-INSTANCE-REGISTRY)
 *
 * Replaces the placeholder (PageHeader + ImplStatus + RestDump) at
 * /admin#instance-registry AND /admin/instance-registry. Manages the
 * :InstanceRegistry singleton: list, add, delete peer Shepard instances.
 *
 * The backend exposes a whole-list RFC 7396 PATCH endpoint. add/delete
 * are implemented client-side by re-PATCHing the modified list — see
 * useInstanceRegistryAdmin for the wire details.
 *
 * Per the brief: baseUrl is the one URL field intentionally surfaced to
 * the operator — peer-instance discovery requires it.
 */
import { AdminFragments } from "./adminMenuItems";
import { useInstanceRegistryAdmin } from "~/composables/context/admin/useInstanceRegistryAdmin";
import type { RegisteredInstance } from "~/composables/context/admin/useInstanceRegistryAdmin";

const {
  instances,
  isLoading,
  isSaving,
  error,
  refresh,
  addInstance,
  deleteInstance,
} = useInstanceRegistryAdmin();

const headers = [
  { title: "Instance ID", key: "instanceId", sortable: true },
  { title: "Display name", key: "displayName", sortable: true },
  { title: "Base URL", key: "baseUrl", sortable: false },
  { title: "DLR institute", key: "dlrInstitute", sortable: true },
  { title: "", key: "actions", sortable: false, width: 80 },
] as const;

// ─── Add form state ──────────────────────────────────────────────────────────
const addDialogOpen = ref(false);
const newInstanceId = ref("");
const newDisplayName = ref("");
const newBaseUrl = ref("");
const newDlrInstitute = ref("");
const addError = ref<string | null>(null);

const instanceIdError = computed(() => {
  const v = newInstanceId.value.trim();
  if (v === "") return null;
  // Short slug — lowercase, alphanumeric + dash. Keeps the badge tooltip readable.
  if (!/^[a-z0-9][a-z0-9-]{1,63}$/.test(v)) {
    return "Use lowercase letters, digits, and dashes (e.g. dlr-augsburg).";
  }
  if (instances.value.some((i) => i.instanceId === v)) {
    return "An instance with this ID already exists. Delete it first to replace.";
  }
  return null;
});

const baseUrlError = computed(() => {
  const v = newBaseUrl.value.trim();
  if (v === "") return null; // optional
  try {
    const u = new URL(v);
    if (u.protocol !== "https:" && u.protocol !== "http:") {
      return "Must be an http(s) URL.";
    }
    return null;
  } catch {
    return "Not a valid URL.";
  }
});

const canAdd = computed(
  () =>
    newInstanceId.value.trim() !== "" &&
    instanceIdError.value === null &&
    baseUrlError.value === null,
);

function openAdd() {
  addError.value = null;
  newInstanceId.value = "";
  newDisplayName.value = "";
  newBaseUrl.value = "";
  newDlrInstitute.value = "";
  addDialogOpen.value = true;
}

async function submitAdd() {
  if (!canAdd.value) return;
  addError.value = null;
  const next: RegisteredInstance = {
    instanceId: newInstanceId.value.trim(),
    displayName: newDisplayName.value.trim() || null,
    baseUrl: newBaseUrl.value.trim() || null,
    dlrInstitute: newDlrInstitute.value.trim() || null,
  };
  const ok = await addInstance(next);
  if (ok) {
    addDialogOpen.value = false;
  } else {
    addError.value = error.value ?? "Failed to add instance.";
  }
}

// ─── Delete confirm ──────────────────────────────────────────────────────────
const deleteTarget = ref<RegisteredInstance | null>(null);
const deleteError = ref<string | null>(null);

function askDelete(row: RegisteredInstance) {
  deleteError.value = null;
  deleteTarget.value = row;
}

async function confirmDelete() {
  const target = deleteTarget.value;
  if (!target) return;
  const ok = await deleteInstance(target.instanceId);
  if (ok) {
    deleteTarget.value = null;
  } else {
    deleteError.value = error.value ?? "Failed to delete instance.";
  }
}
</script>

<template>
  <div :id="AdminFragments.INSTANCE_REGISTRY" class="d-flex flex-column ga-4">
    <div class="d-flex align-center justify-space-between flex-wrap ga-2">
      <div class="d-flex align-center ga-3">
        <h4 class="text-h4">Instance Registry</h4>
        <v-btn
          icon="mdi-refresh"
          variant="text"
          size="small"
          :loading="isLoading"
          aria-label="Refresh"
          @click="refresh"
        />
      </div>
      <v-btn
        color="primary"
        variant="tonal"
        prepend-icon="mdi-plus"
        @click="openAdd"
      >
        Add instance
      </v-btn>
    </div>

    <p class="text-body-2 text-medium-emphasis">
      Register peer Shepard instances so badge hover-text in the provenance
      UI shows a friendly name (e.g. <em>DLR BT, Augsburg</em>) instead of
      the raw <code>instanceId</code>. Default is empty — operator opt-in.
      The <code>baseUrl</code> field is the one URL surfaced to the operator
      by intent (peer-instance discovery requires it).
    </p>

    <v-alert
      v-if="error && !addDialogOpen && !deleteTarget"
      type="error"
      variant="tonal"
      closable
      @click:close="error = null"
    >
      {{ error }}
    </v-alert>

    <v-progress-linear v-if="isLoading && instances.length === 0" indeterminate />

    <v-card v-if="instances.length > 0" variant="outlined">
      <v-data-table
        :headers="headers as unknown as []"
        :items="instances"
        item-value="instanceId"
        density="comfortable"
        :items-per-page="20"
      >
        <template #[`item.instanceId`]="{ item }">
          <code class="text-body-2 font-weight-medium">{{ item.instanceId }}</code>
        </template>

        <template #[`item.displayName`]="{ item }">
          <span v-if="item.displayName">{{ item.displayName }}</span>
          <span v-else class="text-medium-emphasis">—</span>
        </template>

        <template #[`item.baseUrl`]="{ item }">
          <a
            v-if="item.baseUrl"
            :href="item.baseUrl"
            target="_blank"
            rel="noopener noreferrer"
            class="text-decoration-none"
          >
            <code class="text-body-2">{{ item.baseUrl }}</code>
            <v-icon size="x-small" class="ml-1">mdi-open-in-new</v-icon>
          </a>
          <span v-else class="text-medium-emphasis">—</span>
        </template>

        <template #[`item.dlrInstitute`]="{ item }">
          <v-chip v-if="item.dlrInstitute" size="x-small" variant="tonal">
            {{ item.dlrInstitute }}
          </v-chip>
          <span v-else class="text-medium-emphasis">—</span>
        </template>

        <template #[`item.actions`]="{ item }">
          <v-btn
            icon="mdi-delete-outline"
            variant="text"
            size="small"
            color="error"
            aria-label="Delete instance"
            @click="askDelete(item)"
          />
        </template>
      </v-data-table>
    </v-card>

    <v-card
      v-else-if="!isLoading"
      variant="outlined"
      class="pa-6 text-center text-body-2 text-medium-emphasis"
    >
      No peer instances registered yet. Use
      <strong>Add instance</strong> above to register one.
    </v-card>

    <!-- ── Add dialog ─────────────────────────────────────────────────────── -->
    <v-dialog v-model="addDialogOpen" max-width="560">
      <v-card>
        <v-card-title class="text-h6 pa-4 d-flex align-center ga-2">
          <v-icon icon="mdi-map-marker-plus-outline" />
          Add peer instance
        </v-card-title>

        <v-card-text class="pa-4 d-flex flex-column ga-2">
          <v-alert v-if="addError" type="error" density="compact" variant="tonal">
            {{ addError }}
          </v-alert>

          <v-text-field
            v-model="newInstanceId"
            label="Instance ID"
            :error-messages="instanceIdError ?? undefined"
            placeholder="dlr-augsburg"
            hint="Short slug — lowercase, dashes only. Used as the key in the badge tooltip."
            persistent-hint
            variant="outlined"
            density="comfortable"
            autofocus
          />
          <v-text-field
            v-model="newDisplayName"
            label="Display name"
            placeholder="DLR BT, Augsburg"
            hint="Friendly label shown in the provenance badge hover."
            persistent-hint
            variant="outlined"
            density="comfortable"
          />
          <v-text-field
            v-model="newBaseUrl"
            label="Base URL"
            :error-messages="baseUrlError ?? undefined"
            placeholder="https://shepard-api.intra.dlr.de"
            hint="Base URL of the peer instance — used for deep-links."
            persistent-hint
            variant="outlined"
            density="comfortable"
          />
          <v-text-field
            v-model="newDlrInstitute"
            label="DLR institute (optional)"
            placeholder="BT"
            hint="Optional informational tag (not validated)."
            persistent-hint
            variant="outlined"
            density="comfortable"
          />
        </v-card-text>

        <v-card-actions class="pa-4 pt-0">
          <v-spacer />
          <v-btn variant="text" :disabled="isSaving" @click="addDialogOpen = false">
            Cancel
          </v-btn>
          <v-btn
            color="primary"
            variant="tonal"
            :loading="isSaving"
            :disabled="!canAdd"
            @click="submitAdd"
          >
            Add
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- ── Delete confirm dialog ─────────────────────────────────────────── -->
    <v-dialog
      :model-value="deleteTarget !== null"
      max-width="480"
      @update:model-value="(v: boolean) => !v && (deleteTarget = null)"
    >
      <v-card>
        <v-card-title class="text-h6 pa-4 d-flex align-center ga-2">
          <v-icon icon="mdi-delete-outline" color="error" />
          Delete instance
        </v-card-title>
        <v-card-text class="pa-4">
          <v-alert v-if="deleteError" type="error" density="compact" variant="tonal" class="mb-3">
            {{ deleteError }}
          </v-alert>
          <p class="text-body-2 mb-2">
            Remove
            <code>{{ deleteTarget?.instanceId }}</code>
            from the instance registry? Provenance badges referencing this
            instance will fall back to the raw <code>instanceId</code>.
          </p>
        </v-card-text>
        <v-card-actions class="pa-4 pt-0">
          <v-spacer />
          <v-btn variant="text" :disabled="isSaving" @click="deleteTarget = null">
            Cancel
          </v-btn>
          <v-btn
            color="error"
            variant="tonal"
            :loading="isSaving"
            @click="confirmDelete"
          >
            Delete
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>
