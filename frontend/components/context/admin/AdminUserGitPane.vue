<script setup lang="ts">
/**
 * ADM-USR-GIT — admin preseeds / rotates another user's git credential.
 *
 * Replaces the PlaceholderFragmentPane at `/admin#users-git`. Backlog row:
 * PLACEHOLDER-REPLACE-ADM-USR-GIT.
 *
 * Backend gap (tracked in ADM-USR-GIT-BACKEND-1): there is no
 * GET-for-other-users, no separate /rotate endpoint, and no `lastRotatedAt`
 * field on the wire. Today the pane can only SET / REPLACE a credential
 * for (user × host) — the same POST acts as "rotate" when the same host
 * is reused (idempotent replace).
 */
import type { User } from "@dlr-shepard/backend-client";
import { AdminFragments } from "./adminMenuItems";
import {
  useAdminUserGitCredential,
  type AdminGitCredentialBody,
} from "~/composables/context/admin/useAdminUserGitCredential";
import UserSearchAutocomplete from "~/components/admin/UserSearchAutocomplete.vue";

const { isSaving, error, lastResult, setCredential } =
  useAdminUserGitCredential();

const selectedUser = ref<User | null>(null);
const host = ref<string>("");
const gitUsername = ref<string>("");
const pat = ref<string>("");
const displayName = ref<string>("");
const showPat = ref<boolean>(false);
const successMessage = ref<string | null>(null);

function onUserSelected(u: User | null) {
  selectedUser.value = u;
  successMessage.value = null;
}

const formError = computed<string | null>(() => {
  if (!selectedUser.value) return null;
  if (!host.value.trim()) return "host is required (e.g. gitlab.dlr.de).";
  if (!gitUsername.value.trim()) return "git username is required.";
  if (!pat.value) return "Personal Access Token is required.";
  return null;
});

const canSave = computed(
  () => !!selectedUser.value && !formError.value && !isSaving.value,
);

async function onSave() {
  if (!selectedUser.value || !canSave.value) return;
  successMessage.value = null;
  const body: AdminGitCredentialBody = {
    host: host.value.trim(),
    username: gitUsername.value.trim(),
    pat: pat.value,
    displayName: displayName.value.trim() || undefined,
  };
  const res = await setCredential(selectedUser.value.username, body);
  if (res) {
    successMessage.value =
      `Credential saved for ${selectedUser.value.username} on host ${res.host}` +
      ` (appId: ${res.appId.slice(0, 8)}…).`;
    pat.value = "";
  }
}
</script>

<template>
  <div :id="AdminFragments.USERS_GIT" class="d-flex flex-column ga-4">
    <h4 class="text-h4">User git credentials</h4>

    <p class="text-body-2 text-medium-emphasis">
      Set or rotate a git-host Personal Access Token for another user. Used
      by the importer plugin (clone private repos as that user) and the
      wiki-writer plugin (push generated docs).
    </p>

    <v-alert type="warning" variant="tonal" density="compact">
      <strong>Backend gap (ADM-USR-GIT-BACKEND-1):</strong>
      the backend exposes only an idempotent
      <code>POST /v2/admin/users/{username}/git-credentials</code>; there
      is no list endpoint, no explicit <code>/rotate</code>, and no
      <code>lastRotatedAt</code> on the wire. POSTing the same host
      replaces the existing credential (effectively a rotate). This pane
      can therefore SET / REPLACE a credential but cannot show what
      credentials a user already has.
    </v-alert>

    <v-card variant="outlined">
      <v-card-title class="d-flex align-center ga-2 pt-4 pb-2">
        <v-icon color="primary">mdi-source-branch-plus</v-icon>
        Set credential
      </v-card-title>
      <v-card-text>
        <div class="d-flex flex-column ga-3">
          <UserSearchAutocomplete
            label="Target user"
            @user-selected="onUserSelected"
          />

          <v-text-field
            v-model="host"
            label="Git host"
            placeholder="gitlab.dlr.de"
            hint="Hostname only (no scheme). Reuse the same host to rotate."
            persistent-hint
            variant="outlined"
            density="comfortable"
            :disabled="!selectedUser || isSaving"
            data-testid="admin-git-host"
          />

          <v-text-field
            v-model="gitUsername"
            label="Git username"
            hint="Username on the git host (often the same as Shepard username; not always)."
            persistent-hint
            variant="outlined"
            density="comfortable"
            :disabled="!selectedUser || isSaving"
            data-testid="admin-git-username"
          />

          <v-text-field
            v-model="pat"
            label="Personal Access Token"
            :type="showPat ? 'text' : 'password'"
            :append-inner-icon="showPat ? 'mdi-eye-off' : 'mdi-eye'"
            hint="Stored AES-256-GCM encrypted. Never returned over the wire."
            persistent-hint
            variant="outlined"
            density="comfortable"
            :disabled="!selectedUser || isSaving"
            data-testid="admin-git-pat"
            @click:append-inner="showPat = !showPat"
          />

          <v-text-field
            v-model="displayName"
            label="Display name (optional)"
            hint="Defaults to the host if blank."
            persistent-hint
            variant="outlined"
            density="comfortable"
            :disabled="!selectedUser || isSaving"
            data-testid="admin-git-displayname"
          />

          <div class="d-flex align-center ga-2">
            <v-btn
              color="primary"
              variant="tonal"
              :disabled="!canSave"
              :loading="isSaving"
              prepend-icon="mdi-content-save-outline"
              data-testid="admin-git-save"
              @click="onSave"
            >
              Set / Rotate
            </v-btn>
            <span
              v-if="formError"
              class="text-caption text-medium-emphasis"
              data-testid="admin-git-form-error"
            >
              {{ formError }}
            </span>
          </div>

          <v-alert
            v-if="successMessage"
            type="success"
            variant="tonal"
            density="compact"
            data-testid="admin-git-success"
          >
            {{ successMessage }}
          </v-alert>
          <v-alert
            v-if="error"
            type="error"
            variant="tonal"
            density="compact"
            data-testid="admin-git-error"
          >
            {{ error }}
          </v-alert>
          <v-alert
            v-if="lastResult"
            type="info"
            variant="tonal"
            density="compact"
            data-testid="admin-git-last-result"
          >
            Last saved: <code>{{ lastResult.username }}@{{ lastResult.host }}</code>
            (credential appId <code>{{ lastResult.appId }}</code>).
          </v-alert>
        </div>
      </v-card-text>
    </v-card>
  </div>
</template>
