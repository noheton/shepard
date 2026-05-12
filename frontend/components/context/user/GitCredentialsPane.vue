<script setup lang="ts">
import type {
  CreateGitCredentialIO,
  GitCredentialIO,
  PatchGitCredentialIO,
} from "@dlr-shepard/backend-client";
import { useFetchGitCredentials } from "~/composables/context/useFetchGitCredentials";
import { useManageGitCredentials } from "~/composables/context/useManageGitCredentials";

const { credentials, isLoading, refresh } = useFetchGitCredentials();
const { create, patch, remove, isSaving, saveError } = useManageGitCredentials();

const showAddDialog = ref(false);
const showEditDialog = ref(false);
const showDeleteDialog = ref(false);
const editingCredential = ref<GitCredentialIO | null>(null);
const deletingAppId = ref<string | null>(null);

const showAddPat = ref(false);
const showEditPat = ref(false);

const addForm = ref<CreateGitCredentialIO>({
  host: "",
  displayName: "",
  username: "",
  pat: "",
});

const editForm = ref<PatchGitCredentialIO>({
  displayName: "",
  username: "",
  pat: "",
});

function openAddDialog() {
  addForm.value = { host: "", displayName: "", username: "", pat: "" };
  showAddPat.value = false;
  showAddDialog.value = true;
}

function openEditDialog(credential: GitCredentialIO) {
  editingCredential.value = credential;
  editForm.value = {
    displayName: credential.displayName ?? "",
    username: credential.username,
    pat: "",
  };
  showEditPat.value = false;
  showEditDialog.value = true;
}

function openDeleteDialog(appId: string) {
  deletingAppId.value = appId;
  showDeleteDialog.value = true;
}

const addFormValid = computed(
  () =>
    addForm.value.host.trim().length > 0 &&
    addForm.value.username.trim().length > 0 &&
    addForm.value.pat.trim().length > 0,
);

async function submitAdd() {
  const result = await create({
    host: addForm.value.host.trim(),
    displayName: addForm.value.displayName?.trim() || null,
    username: addForm.value.username.trim(),
    pat: addForm.value.pat,
  });
  if (result) {
    showAddDialog.value = false;
    await refresh();
  }
}

async function submitEdit() {
  if (!editingCredential.value) return;
  const body: PatchGitCredentialIO = {
    displayName: editForm.value.displayName?.trim() || null,
    username: editForm.value.username?.trim() || undefined,
  };
  if (editForm.value.pat && editForm.value.pat.trim().length > 0) {
    body.pat = editForm.value.pat.trim();
  }
  const result = await patch(editingCredential.value.appId, body);
  if (result) {
    showEditDialog.value = false;
    await refresh();
  }
}

async function confirmDelete() {
  if (!deletingAppId.value) return;
  const ok = await remove(deletingAppId.value);
  if (ok) {
    await refresh();
  }
}

function truncateAppId(appId: string): string {
  return appId.length > 12 ? appId.slice(0, 12) + "…" : appId;
}
</script>

<template>
  <div>
    <div class="top-row">
      <h4 class="text-h4">Git Credentials</h4>
      <ExpansionPanelTitleButton
        icon="mdi-plus-circle"
        text="ADD"
        @click="openAddDialog"
      />
    </div>

    <CenteredLoadingSpinner v-if="isLoading" />
    <div v-else>
      <v-alert
        v-if="saveError"
        type="error"
        class="mb-4"
        closable
        @click:close="saveError = null"
      >
        {{ saveError }}
      </v-alert>
      <div v-if="credentials.length === 0">No git credentials linked yet.</div>
      <v-table v-else hover>
        <thead>
          <tr>
            <th>Host</th>
            <th>Display Name</th>
            <th>Username</th>
            <th>App ID</th>
            <th />
          </tr>
        </thead>
        <tbody>
          <tr v-for="cred in credentials" :key="cred.appId">
            <td>{{ cred.host }}</td>
            <td>{{ cred.displayName ?? "—" }}</td>
            <td>{{ cred.username }}</td>
            <td class="app-id-column" :title="cred.appId">
              {{ truncateAppId(cred.appId) }}
            </td>
            <td class="action-column">
              <ActionButton
                icon="mdi-pencil-outline"
                @click="openEditDialog(cred)"
              />
              <ActionButton
                icon="mdi-delete-outline"
                color="error"
                @click="openDeleteDialog(cred.appId)"
              />
            </td>
          </tr>
        </tbody>
      </v-table>
    </div>

    <v-dialog
      v-model="showAddDialog"
      max-width="560"
      persistent
      @keydown.esc="showAddDialog = false"
    >
      <v-card color="canvas">
        <template #title>
          <div class="d-flex justify-space-between align-baseline">
            <div class="text-h4">Add Git Credential</div>
            <v-btn
              variant="plain"
              icon="mdi-close"
              @click="showAddDialog = false"
            />
          </div>
        </template>
        <template #text>
          <v-alert v-if="saveError" type="error" class="mb-4">
            {{ saveError }}
          </v-alert>
          <v-text-field
            v-model="addForm.host"
            label="Host"
            placeholder="gitlab.com"
            :disabled="isSaving"
            required
            class="mb-2"
          />
          <v-text-field
            v-model="addForm.displayName"
            label="Display Name"
            placeholder="Work account"
            :disabled="isSaving"
            class="mb-2"
          />
          <v-text-field
            v-model="addForm.username"
            label="Username"
            :disabled="isSaving"
            required
            class="mb-2"
          />
          <v-text-field
            v-model="addForm.pat"
            label="Personal Access Token"
            :type="showAddPat ? 'text' : 'password'"
            :append-inner-icon="showAddPat ? 'mdi-eye-off' : 'mdi-eye'"
            :disabled="isSaving"
            required
            class="mb-2"
            @click:append-inner="showAddPat = !showAddPat"
          />
        </template>
        <template #actions>
          <v-spacer />
          <v-btn
            variant="flat"
            color="treeview"
            :disabled="isSaving"
            @click="showAddDialog = false"
          >
            Cancel
          </v-btn>
          <v-btn
            variant="flat"
            color="primary"
            :disabled="!addFormValid || isSaving"
            :loading="isSaving"
            @click="submitAdd"
          >
            Add
          </v-btn>
        </template>
      </v-card>
    </v-dialog>

    <v-dialog
      v-model="showEditDialog"
      max-width="560"
      persistent
      @keydown.esc="showEditDialog = false"
    >
      <v-card color="canvas">
        <template #title>
          <div class="d-flex justify-space-between align-baseline">
            <div class="text-h4">Edit Git Credential</div>
            <v-btn
              variant="plain"
              icon="mdi-close"
              @click="showEditDialog = false"
            />
          </div>
        </template>
        <template #text>
          <v-alert v-if="saveError" type="error" class="mb-4">
            {{ saveError }}
          </v-alert>
          <v-text-field
            v-model="editForm.displayName"
            label="Display Name"
            placeholder="Work account"
            :disabled="isSaving"
            class="mb-2"
          />
          <v-text-field
            v-model="editForm.username"
            label="Username"
            :disabled="isSaving"
            class="mb-2"
          />
          <v-text-field
            v-model="editForm.pat"
            label="Personal Access Token"
            :type="showEditPat ? 'text' : 'password'"
            :append-inner-icon="showEditPat ? 'mdi-eye-off' : 'mdi-eye'"
            placeholder="Leave blank to keep current PAT"
            :disabled="isSaving"
            class="mb-2"
            @click:append-inner="showEditPat = !showEditPat"
          />
        </template>
        <template #actions>
          <v-spacer />
          <v-btn
            variant="flat"
            color="treeview"
            :disabled="isSaving"
            @click="showEditDialog = false"
          >
            Cancel
          </v-btn>
          <v-btn
            variant="flat"
            color="primary"
            :disabled="isSaving"
            :loading="isSaving"
            @click="submitEdit"
          >
            Save
          </v-btn>
        </template>
      </v-card>
    </v-dialog>

    <ConfirmDeleteDialog
      v-if="showDeleteDialog"
      v-model:show-dialog="showDeleteDialog"
      prompt-text="Delete this git credential? This cannot be undone."
      @confirmed="confirmDelete"
    />
  </div>
</template>

<style scoped lang="scss">
td {
  white-space: nowrap;
}
.app-id-column {
  width: 100%;
  font-family: monospace;
  font-size: 0.85em;
}
.action-column {
  text-align: center;
  white-space: nowrap;
}
.top-row {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  margin-bottom: 16px;
}
.v-table {
  background-color: unset;

  :deep(thead) > tr > th {
    background-color: rgb(var(--v-theme-divider2));
  }

  :deep(td) {
    padding: 8px 24px !important;
  }

  :deep(tr):hover {
    background-color: rgb(var(--v-theme-focus1));
  }

  :deep(th) {
    font-size: 16px;
    padding: 8px 24px !important;
  }

  :deep(.mdi) {
    margin-left: 0.2em;
  }
}
</style>
