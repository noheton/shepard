<script setup lang="ts">
import { useFetchUserProfile } from "~/composables/context/useFetchUserProfile";
import { usePatchMe } from "~/composables/context/usePatchMe";
import { useJupyterPreference } from "~/composables/context/useJupyterPreference";
import { useAdvancedMode } from "~/composables/context/useAdvancedMode";

const { user, isLoading } = useFetchUserProfile();
const { patchMe, isSaving } = usePatchMe();
const { preferredJupyterUrl, isSaving: isJupyterSaving, save: saveJupyter } = useJupyterPreference();
const { advancedMode, isSaving: isAdvancedSaving, setAdvancedMode } = useAdvancedMode();

const editDialog = ref(false);
const editOrcid = ref<string>("");
const editDisplayName = ref<string>("");

const jupyterUrlInput = ref<string>("");

watch(
  preferredJupyterUrl,
  (url) => {
    jupyterUrlInput.value = url;
  },
  { immediate: true },
);

function openEdit() {
  editOrcid.value = user.value?.orcid ?? "";
  editDisplayName.value = user.value?.displayName ?? "";
  editDialog.value = true;
}

async function saveEdit() {
  const updated = await patchMe({
    orcid: editOrcid.value.trim() === "" ? null : editOrcid.value.trim(),
    displayName:
      editDisplayName.value.trim() === ""
        ? null
        : editDisplayName.value.trim(),
  });
  if (updated) {
    user.value = updated;
    editDialog.value = false;
  }
}

async function saveJupyterUrl() {
  await saveJupyter(jupyterUrlInput.value.trim());
}
</script>

<template>
  <div class="d-flex flex-column ga-4">
    <div class="d-flex align-center justify-space-between">
      <h4 class="text-h4">Profile</h4>
      <v-btn
        v-if="user && !isLoading"
        variant="tonal"
        size="small"
        prepend-icon="mdi-pencil"
        @click="openEdit"
      >
        Edit
      </v-btn>
    </div>

    <centered-loading-spinner v-if="isLoading" />
    <v-table v-if="user && !isLoading" class="table">
      <tbody>
        <tr>
          <th>Username</th>
          <td>{{ user.username }}</td>
        </tr>
        <tr v-if="user.effectiveDisplayName">
          <th>Display Name</th>
          <td>{{ user.effectiveDisplayName }}</td>
        </tr>
        <tr>
          <th>First Name</th>
          <td>{{ user.firstName }}</td>
        </tr>
        <tr>
          <th>Last Name</th>
          <td>{{ user.lastName }}</td>
        </tr>
        <tr>
          <th>E-Mail</th>
          <td>{{ user.email }}</td>
        </tr>
        <tr>
          <th>ORCID</th>
          <td>
            <a
              v-if="user.orcid"
              :href="`https://orcid.org/${user.orcid}`"
              target="_blank"
              rel="noopener noreferrer"
            >
              {{ user.orcid }}
            </a>
            <span v-else class="text-disabled">Not set</span>
          </td>
        </tr>
      </tbody>
    </v-table>

    <!-- JupyterHub URL section -->
    <div v-if="user && !isLoading" class="d-flex flex-column ga-2">
      <h5 class="text-h5">JupyterHub</h5>
      <v-text-field
        v-model="jupyterUrlInput"
        label="JupyterHub base URL"
        placeholder="https://myhub.example.com"
        hint="Set your JupyterHub base URL to enable 'Open in JupyterHub' buttons. Stored in your user preferences."
        persistent-hint
        variant="outlined"
        density="comfortable"
        clearable
        :loading="isJupyterSaving"
        :disabled="isJupyterSaving"
      >
        <template #append>
          <v-btn
            color="primary"
            variant="flat"
            density="comfortable"
            :loading="isJupyterSaving"
            :disabled="isJupyterSaving"
            @click="saveJupyterUrl"
          >
            Save
          </v-btn>
        </template>
      </v-text-field>
    </div>

    <!-- Advanced mode toggle -->
    <div v-if="user && !isLoading" class="d-flex flex-column ga-2">
      <h5 class="text-h5">Display settings</h5>
      <v-switch
        :model-value="advancedMode"
        label="Advanced mode"
        :loading="isAdvancedSaving"
        :disabled="isAdvancedSaving"
        color="primary"
        density="comfortable"
        hide-details
        @update:model-value="val => setAdvancedMode(Boolean(val))"
      />
      <p class="text-body-2 text-medium-emphasis">
        Shows advanced features like container management and low-level data views. Off by default for a simpler experience.
      </p>
    </div>

    <!-- Edit dialog -->
    <v-dialog v-model="editDialog" max-width="480">
      <v-card>
        <v-card-title>Edit Profile</v-card-title>
        <v-card-text>
          <v-text-field
            v-model="editDisplayName"
            label="Display Name"
            hint="Overrides first/last name in the UI. Leave blank to use your name."
            persistent-hint
            class="mb-4"
          />
          <v-text-field
            v-model="editOrcid"
            label="ORCID"
            placeholder="0000-0002-1825-0097"
            hint="16-digit identifier from orcid.org. Leave blank to clear."
            persistent-hint
          />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" :disabled="isSaving" @click="editDialog = false">
            Cancel
          </v-btn>
          <v-btn
            variant="tonal"
            color="primary"
            :loading="isSaving"
            @click="saveEdit"
          >
            Save
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<style scoped lang="scss"></style>
