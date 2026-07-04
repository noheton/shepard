<script setup lang="ts">
/**
 * ADM-USR-GIT — admin preseeds / rotates another user's git credentials.
 *
 * Replaces the PlaceholderFragmentPane at `/admin#users-git`. Backlog row:
 * PLACEHOLDER-REPLACE-ADM-USR-GIT.
 *
 * ADM-USR-GIT-BACKEND-1-FE (2026-05-31): now consumes the list endpoint
 * (`GET .../git-credentials`) and the explicit rotate endpoint
 * (`POST .../{appId}/rotate`) shipped 2026-05-31 (backend 1935128eb).
 */
import type { User } from "@dlr-shepard/backend-client";
import { AdminFragments } from "./adminMenuItems";
import {
  useAdminUserGitCredential,
  type AdminGitCredentialBody,
  type AdminGitCredentialListItem,
} from "~/composables/context/admin/useAdminUserGitCredential";
import UserSearchAutocomplete from "~/components/admin/UserSearchAutocomplete.vue";

const {
  isSaving,
  isLoading,
  error,
  lastResult,
  items,
  setCredential,
  listCredentials,
  rotateCredential,
} = useAdminUserGitCredential();

const selectedUser = ref<User | null>(null);
const host = ref<string>("");
const gitUsername = ref<string>("");
const pat = ref<string>("");
const displayName = ref<string>("");
const showPat = ref<boolean>(false);
const successMessage = ref<string | null>(null);

// ─── Rotate dialog state (ADM-USR-GIT-BACKEND-1-FE) ────────────────────
const rotateOpen = ref(false);
const rotating = ref<AdminGitCredentialListItem | null>(null);
const newPat = ref<string>("");
const showNewPat = ref<boolean>(false);

function onUserSelected(u: User | null) {
  selectedUser.value = u;
  successMessage.value = null;
  if (u) void listCredentials(u.username);
  else items.value = [];
}

function openRotate(c: AdminGitCredentialListItem) {
  rotating.value = c;
  newPat.value = "";
  showNewPat.value = false;
  rotateOpen.value = true;
}

async function onRotateSubmit() {
  if (!selectedUser.value || !rotating.value || !newPat.value) return;
  const ok = await rotateCredential(
    selectedUser.value.username,
    rotating.value.appId,
    newPat.value,
  );
  if (ok) {
    successMessage.value = `Rotated PAT for ${rotating.value.host}.`;
    rotateOpen.value = false;
    rotating.value = null;
    newPat.value = "";
  }
}

function formatRotated(iso: string | null): string {
  if (!iso) return "never";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString();
}

const credentialHeaders = [
  { title: "Host", key: "host" },
  { title: "Git username", key: "username" },
  { title: "Display name", key: "displayName" },
  { title: "Last rotated", key: "lastRotatedAt" },
  { title: "Actions", key: "actions", sortable: false, width: 120 },
];

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
    // ADM-USR-GIT-BACKEND-1-FE: refresh the list so the new credential
    // (or updated lastRotatedAt) shows immediately.
    void listCredentials(selectedUser.value.username);
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

    <!-- ───── Existing credentials (ADM-USR-GIT-BACKEND-1-FE) ───── -->
    <v-card v-if="selectedUser" variant="outlined" data-testid="admin-git-credentials-card">
      <v-card-title class="d-flex align-center ga-2 pt-4 pb-2">
        <v-icon color="primary">mdi-source-branch</v-icon>
        Current credentials for {{ selectedUser.username }}
        <v-chip size="x-small" variant="tonal" class="ml-2">
          {{ items.length }} configured
        </v-chip>
      </v-card-title>
      <v-card-text>
        <v-data-table
          :headers="credentialHeaders"
          :items="items"
          :loading="isLoading"
          items-per-page="10"
          density="compact"
          data-testid="admin-git-credentials-table"
        >
          <template #[`item.displayName`]="{ item }">
            {{ item.displayName ?? '—' }}
          </template>
          <template #[`item.lastRotatedAt`]="{ item }">
            <span class="text-caption">{{ formatRotated(item.lastRotatedAt) }}</span>
          </template>
          <template #[`item.actions`]="{ item }">
            <v-btn
              size="x-small"
              variant="text"
              color="primary"
              prepend-icon="mdi-key-change"
              :data-testid="`admin-git-rotate-${item.appId}`"
              @click="openRotate(item)"
            >
              Rotate
            </v-btn>
          </template>
          <template #no-data>
            <div class="text-caption text-medium-emphasis py-4">
              No git credentials configured for this user yet.
            </div>
          </template>
        </v-data-table>
      </v-card-text>
    </v-card>

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

    <!-- ───── Rotate dialog (ADM-USR-GIT-BACKEND-1-FE) ───── -->
    <v-dialog v-model="rotateOpen" max-width="480">
      <v-card>
        <v-card-title>Rotate PAT</v-card-title>
        <v-card-text>
          <p class="text-body-2 mb-3">
            Replace the stored PAT for
            <strong>{{ rotating?.username }}@{{ rotating?.host }}</strong>.
            The old token is overwritten and <code>lastRotatedAt</code> is
            stamped to now.
          </p>
          <v-text-field
            v-model="newPat"
            label="New Personal Access Token"
            :type="showNewPat ? 'text' : 'password'"
            :append-inner-icon="showNewPat ? 'mdi-eye-off' : 'mdi-eye'"
            variant="outlined"
            density="comfortable"
            data-testid="admin-git-rotate-newpat"
            @click:append-inner="showNewPat = !showNewPat"
          />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="rotateOpen = false">Cancel</v-btn>
          <v-btn
            color="primary"
            variant="tonal"
            :loading="isSaving"
            :disabled="!newPat"
            data-testid="admin-git-rotate-submit"
            @click="onRotateSubmit"
          >
            Rotate
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>
