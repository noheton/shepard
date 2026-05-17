<script setup lang="ts">
import { useInstanceRorConfig } from "~/composables/context/useInstanceRorConfig";
import { AdminFragments } from "./adminMenuItems";

const { config, isLoading, error, refresh, patch } = useInstanceRorConfig();

// ─── Edit dialog state ────────────────────────────────────────────────────
const dialogOpen = ref(false);
const editRorId = ref<string>("");
const editOrgName = ref<string>("");
const isSaving = ref(false);
const saveError = ref<string | null>(null);

// Inline validation
const rorIdError = computed(() => {
  const val = editRorId.value;
  if (!val) return null; // blank is allowed (clears the field)
  if (!/^[A-Za-z0-9]{1,9}$/.test(val)) {
    return "ROR ID must be 1-9 alphanumeric characters (e.g. 04cvxnb49)";
  }
  return null;
});

const canSave = computed(() => rorIdError.value === null);

function openEdit() {
  editRorId.value = config.value?.rorId ?? "";
  editOrgName.value = config.value?.organizationName ?? "";
  saveError.value = null;
  dialogOpen.value = true;
}

function cancelEdit() {
  dialogOpen.value = false;
  saveError.value = null;
}

async function save() {
  if (!canSave.value) return;
  isSaving.value = true;
  saveError.value = null;
  try {
    const rorIdToSend = editRorId.value.trim() || null;
    const orgNameToSend = editOrgName.value.trim() || null;
    const result = await patch(rorIdToSend, orgNameToSend);
    if (result) {
      dialogOpen.value = false;
      await refresh();
    } else {
      saveError.value = "Failed to save. Please try again.";
    }
  } finally {
    isSaving.value = false;
  }
}
</script>

<template>
  <div :id="AdminFragments.INSTANCE_ROR" class="d-flex flex-column ga-4">
    <div class="d-flex align-center justify-space-between flex-wrap ga-2">
      <h4 class="text-h4">Research Organization</h4>
      <v-btn
        variant="tonal"
        color="primary"
        prepend-icon="mdi-pencil-outline"
        :disabled="isLoading"
        @click="openEdit"
      >
        Edit
      </v-btn>
    </div>

    <v-alert
      v-if="error"
      type="error"
      closable
      @click:close="error = null"
    >
      {{ error }}
    </v-alert>

    <centered-loading-spinner v-if="isLoading" />

    <v-card v-else variant="outlined">
      <v-card-title class="d-flex align-center ga-2 bg-surface-variant">
        <v-icon icon="mdi-office-building" />
        <span>ROR Configuration</span>
      </v-card-title>

      <v-card-text class="pa-4">
        <template v-if="config?.rorId">
          <v-list density="compact" lines="two">
            <v-list-item>
              <v-list-item-title class="text-medium-emphasis text-caption">
                ROR ID
              </v-list-item-title>
              <v-list-item-subtitle class="text-body-1 font-weight-medium mt-1">
                {{ config.rorId }}
              </v-list-item-subtitle>
            </v-list-item>

            <v-list-item v-if="config.organizationName">
              <v-list-item-title class="text-medium-emphasis text-caption">
                Organization Name
              </v-list-item-title>
              <v-list-item-subtitle class="text-body-1 mt-1">
                {{ config.organizationName }}
              </v-list-item-subtitle>
            </v-list-item>

            <v-list-item v-if="config.rorUrl">
              <v-list-item-title class="text-medium-emphasis text-caption">
                ROR URL
              </v-list-item-title>
              <v-list-item-subtitle class="mt-1">
                <a
                  :href="config.rorUrl"
                  target="_blank"
                  rel="noopener noreferrer"
                  class="text-body-1"
                >
                  {{ config.rorUrl }}
                  <v-icon icon="mdi-open-in-new" size="x-small" class="ml-1" />
                </a>
              </v-list-item-subtitle>
            </v-list-item>
          </v-list>
        </template>

        <template v-else>
          <div class="d-flex flex-column align-center ga-3 py-6">
            <v-icon icon="mdi-office-building-outline" size="48" color="medium-emphasis" />
            <div class="text-center">
              <p class="text-body-1 font-weight-medium">Not configured</p>
              <p class="text-body-2 text-medium-emphasis mt-1">
                Set your institution's ROR ID to enrich exported datasets and provenance records.
              </p>
            </div>
          </div>
        </template>
      </v-card-text>
    </v-card>

    <!-- Edit dialog -->
    <v-dialog v-model="dialogOpen" max-width="520">
      <v-card>
        <v-card-title class="text-h6 pa-4 d-flex align-center ga-2">
          <v-icon icon="mdi-office-building" />
          Edit Research Organization
        </v-card-title>

        <v-card-text class="pa-4">
          <v-alert v-if="saveError" type="error" class="mb-4">
            {{ saveError }}
          </v-alert>

          <v-text-field
            v-model="editRorId"
            label="ROR ID"
            :error-messages="rorIdError ?? undefined"
            placeholder="04cvxnb49"
            hint="Suffix only — e.g. 04cvxnb49 for DLR. Leave blank to clear."
            persistent-hint
            variant="outlined"
            density="comfortable"
            class="mb-2"
          />

          <v-text-field
            v-model="editOrgName"
            label="Organization Name"
            placeholder="DLR e.V."
            hint="Human-readable name for display in exports and provenance records."
            persistent-hint
            variant="outlined"
            density="comfortable"
          />
        </v-card-text>

        <v-card-actions class="pa-4 pt-0">
          <v-spacer />
          <v-btn
            variant="text"
            :disabled="isSaving"
            @click="cancelEdit"
          >
            Cancel
          </v-btn>
          <v-btn
            color="primary"
            variant="tonal"
            :loading="isSaving"
            :disabled="!canSave"
            @click="save"
          >
            Save
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<style scoped lang="scss"></style>
