<script setup lang="ts">
/**
 * ADM-USR-ORCID — set / clear another user's ORCID iD.
 *
 * Replaces the PlaceholderFragmentPane previously mounted at
 * `/admin#users-orcid`. Backlog row: PLACEHOLDER-REPLACE-ADM-USR-ORCID.
 *
 * The self-edit path is `/me#profile` (PATCH /v2/users/me) shipped in RDM-002;
 * this pane is the admin override for deactivated / audit-handoff cases.
 */
import type { User } from "@dlr-shepard/backend-client";
import { AdminFragments } from "./adminMenuItems";
import { useAdminUserOrcid } from "~/composables/context/admin/useAdminUserOrcid";
import { isValidOrcid } from "~/utils/orcidFormat";
import UserSearchAutocomplete from "~/components/admin/UserSearchAutocomplete.vue";

const { isSaving, error, patchOrcid } = useAdminUserOrcid();

const selectedUser = ref<User | null>(null);
const editOrcid = ref<string>("");
const lastSaveMessage = ref<string | null>(null);

function onUserSelected(user: User | null) {
  selectedUser.value = user;
  editOrcid.value = user?.orcid ?? "";
  lastSaveMessage.value = null;
}

const orcidError = computed<string | null>(() => {
  const v = editOrcid.value.trim();
  if (v === "") return null; // blank = clear
  return isValidOrcid(v)
    ? null
    : "Not a valid ORCID (NNNN-NNNN-NNNN-NNNX with mod 11-2 checksum).";
});

const canSave = computed(() => {
  if (!selectedUser.value) return false;
  if (orcidError.value) return false;
  const trimmed = editOrcid.value.trim();
  const current = selectedUser.value.orcid ?? "";
  return trimmed !== current;
});

async function onSave() {
  if (!selectedUser.value || !canSave.value) return;
  lastSaveMessage.value = null;
  const trimmed = editOrcid.value.trim();
  const next: string | null = trimmed === "" ? null : trimmed;
  const ok = await patchOrcid(selectedUser.value.username, next);
  if (ok) {
    lastSaveMessage.value = next
      ? `Set ORCID for ${selectedUser.value.username} to ${next}.`
      : `Cleared ORCID for ${selectedUser.value.username}.`;
    // Reflect the saved state locally so the diff resets.
    (selectedUser.value as { orcid?: string | null }).orcid = next;
  }
}
</script>

<template>
  <div :id="AdminFragments.USERS_ORCID" class="d-flex flex-column ga-4">
    <h4 class="text-h4">User ORCID overrides</h4>

    <p class="text-body-2 text-medium-emphasis">
      Set or clear a user's ORCID iD on their behalf. Use this only when the
      user cannot edit their own profile (deactivated account, audit
      hand-off, demo seed). The normal flow is the user's own
      <code>/me#profile</code> page. Changes are recorded in the provenance
      audit trail.
    </p>

    <v-card variant="outlined">
      <v-card-text>
        <div class="d-flex flex-column ga-3">
          <UserSearchAutocomplete
            label="User"
            @user-selected="onUserSelected"
          />

          <v-text-field
            v-model="editOrcid"
            label="ORCID iD"
            placeholder="0000-0000-0000-0000"
            hint="Format: NNNN-NNNN-NNNN-NNN[N|X]. Leave blank to clear."
            persistent-hint
            variant="outlined"
            density="comfortable"
            :error-messages="orcidError ? [orcidError] : []"
            :disabled="!selectedUser || isSaving"
            data-testid="admin-orcid-input"
          />

          <div class="d-flex align-center ga-2">
            <v-btn
              color="primary"
              variant="tonal"
              :disabled="!canSave"
              :loading="isSaving"
              prepend-icon="mdi-content-save-outline"
              data-testid="admin-orcid-save"
              @click="onSave"
            >
              Save
            </v-btn>
            <span
              v-if="selectedUser?.orcid"
              class="text-caption text-medium-emphasis"
            >
              current: <code data-testid="admin-orcid-current">{{ selectedUser.orcid }}</code>
            </span>
            <span
              v-else-if="selectedUser"
              class="text-caption text-medium-emphasis"
            >
              no ORCID set
            </span>
          </div>

          <v-alert
            v-if="lastSaveMessage"
            type="success"
            variant="tonal"
            density="compact"
            data-testid="admin-orcid-success"
          >
            {{ lastSaveMessage }}
          </v-alert>
          <v-alert
            v-if="error"
            type="error"
            variant="tonal"
            density="compact"
            data-testid="admin-orcid-error"
          >
            {{ error }}
          </v-alert>
        </div>
      </v-card-text>
    </v-card>
  </div>
</template>
