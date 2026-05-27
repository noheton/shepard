<script setup lang="ts">
import type {
  CreateGitReferenceIO,
  GitReferenceIO,
  PatchGitReferenceIO,
} from "@dlr-shepard/backend-client";
import { useFetchGitCredentials } from "~/composables/context/useFetchGitCredentials";
import { useFetchGitReferences } from "~/composables/context/useFetchGitReferences";
import { useManageGitReferences } from "~/composables/context/useManageGitReferences";

const props = defineProps<{ dataObjectAppId: string }>();

const { gitReferences, isLoading, refresh } = useFetchGitReferences(
  props.dataObjectAppId,
);
const { create, patch, remove, isSaving, saveError } =
  useManageGitReferences();

const showCreateDialog = ref(false);
const showEditDialog = ref(false);
const showDeleteDialog = ref(false);
const editTarget = ref<GitReferenceIO | null>(null);
const deleteTarget = ref<GitReferenceIO | null>(null);

const createForm = reactive<CreateGitReferenceIO>({
  repoUrl: "",
  ref: undefined,
  path: undefined,
});

const editForm = reactive<PatchGitReferenceIO>({
  repoUrl: undefined,
  ref: undefined,
  path: undefined,
});

function openCreate() {
  createForm.repoUrl = "";
  createForm.ref = undefined;
  createForm.path = undefined;
  showCreateDialog.value = true;
}

function openEdit(ref: GitReferenceIO) {
  editTarget.value = ref;
  editForm.repoUrl = ref.repoUrl;
  editForm.ref = ref.ref;
  editForm.path = ref.path;
  showEditDialog.value = true;
}

function openDelete(ref: GitReferenceIO) {
  deleteTarget.value = ref;
  showDeleteDialog.value = true;
}

async function submitCreate() {
  if (!createForm.repoUrl) return;
  const result = await create(props.dataObjectAppId, {
    repoUrl: createForm.repoUrl,
    ref: createForm.ref || undefined,
    path: createForm.path || undefined,
  });
  if (result) {
    showCreateDialog.value = false;
    refresh();
  }
}

async function submitEdit() {
  if (!editTarget.value) return;
  const result = await patch(
    props.dataObjectAppId,
    editTarget.value.appId,
    editForm,
  );
  if (result) {
    showEditDialog.value = false;
    refresh();
  }
}

async function confirmDelete() {
  if (!deleteTarget.value) return;
  const ok = await remove(props.dataObjectAppId, deleteTarget.value.appId);
  if (ok) {
    showDeleteDialog.value = false;
    refresh();
  }
}

const { credentials, isLoading: credentialsLoading } = useFetchGitCredentials();
const credentialsBannerDismissed = ref(false);

const urlSuggestions = computed(() =>
  credentials.value.map(c => ({
    title: `https://${c.host}/${c.username}/`,
    subtitle: c.displayName ?? c.host,
  })),
);
</script>

<template>
  <div class="d-flex flex-column ga-4">
    <div class="d-flex align-center justify-space-between">
      <h5 class="text-h5">
        Git References
        <!-- UX Pattern D: low-emphasis count badge so the user knows how many
             entries this section holds without expanding / scrolling. -->
        <span
          class="text-low-emphasis ml-1"
          data-testid="git-references-count"
        >({{ gitReferences.length }})</span>
      </h5>
      <v-btn
        color="primary"
        variant="flat"
        prepend-icon="mdi-plus-circle"
        @click="openCreate"
      >
        Add reference
      </v-btn>
    </div>

    <v-alert v-if="saveError" type="error" closable>{{ saveError }}</v-alert>

    <v-alert
      v-if="!credentialsLoading && credentials.length === 0 && !credentialsBannerDismissed"
      type="info"
      variant="tonal"
      density="compact"
      closable
      @click:close="credentialsBannerDismissed = true"
    >
      No git credentials configured — autocomplete won't work and private repos will be inaccessible.
      <template #append>
        <v-btn
          :to="'/user#git-credentials'"
          variant="text"
          size="small"
        >
          Configure
        </v-btn>
      </template>
    </v-alert>

    <centered-loading-spinner v-if="isLoading" />

    <v-table v-else-if="gitReferences.length > 0" class="table">
      <thead>
        <tr>
          <th>Repository URL</th>
          <th>Ref</th>
          <th>Path</th>
          <th />
        </tr>
      </thead>
      <tbody>
        <tr v-for="gr in gitReferences" :key="gr.appId">
          <td>{{ gr.repoUrl }}</td>
          <td>{{ gr.ref ?? '—' }}</td>
          <td>{{ gr.path ?? '—' }}</td>
          <td class="text-right">
            <v-btn
              icon="mdi-pencil-outline"
              variant="plain"
              density="compact"
              @click="openEdit(gr)"
            />
            <v-btn
              icon="mdi-delete-outline"
              variant="plain"
              density="compact"
              color="error"
              @click="openDelete(gr)"
            />
          </td>
        </tr>
      </tbody>
    </v-table>

    <div v-else class="text-medium-emphasis">No git references linked yet</div>

    <FormDialog
      v-model:show-dialog="showCreateDialog"
      title="Add Git Reference"
      :loading="isSaving || credentialsLoading"
      :submit-disabled="!createForm.repoUrl || isSaving"
      save-button-text="Add"
      @submit="submitCreate"
    >
      <template #form>
        <v-row class="pt-4">
          <v-col cols="12">
            <v-combobox
              v-model="createForm.repoUrl"
              :items="urlSuggestions"
              item-title="title"
              item-value="title"
              label="Repository URL"
              placeholder="https://gitlab.com/user/repo"
              :hint="
                urlSuggestions.length === 0
                  ? 'Add git credentials in your profile to get autocomplete suggestions'
                  : undefined
              "
              persistent-hint
              required
            >
              <template #item="{ item, props: itemProps }">
                <v-list-item v-bind="itemProps" :subtitle="item.raw.subtitle" />
              </template>
            </v-combobox>
          </v-col>
          <v-col cols="12">
            <v-text-field
              v-model="createForm.ref"
              label="Ref"
              placeholder="main"
              hint="Branch, tag, or commit SHA. Leave blank for repository default."
              persistent-hint
              clearable
            />
          </v-col>
          <v-col cols="12">
            <v-text-field
              v-model="createForm.path"
              label="Path"
              placeholder="data/subdir"
              hint="Subdirectory within the repository. Leave blank for root."
              persistent-hint
              clearable
            />
          </v-col>
        </v-row>
      </template>
    </FormDialog>

    <FormDialog
      v-model:show-dialog="showEditDialog"
      title="Edit Git Reference"
      :loading="isSaving"
      :submit-disabled="!editForm.repoUrl || isSaving"
      @submit="submitEdit"
    >
      <template #form>
        <v-row class="pt-4">
          <v-col cols="12">
            <v-text-field
              v-model="editForm.repoUrl"
              label="Repository URL"
              required
            />
          </v-col>
          <v-col cols="12">
            <v-text-field
              v-model="editForm.ref"
              label="Ref"
              placeholder="main"
              hint="Branch, tag, or commit SHA. Leave blank for repository default."
              persistent-hint
              clearable
            />
          </v-col>
          <v-col cols="12">
            <v-text-field
              v-model="editForm.path"
              label="Path"
              placeholder="data/subdir"
              hint="Subdirectory within the repository. Leave blank for root."
              persistent-hint
              clearable
            />
          </v-col>
        </v-row>
      </template>
    </FormDialog>

    <ConfirmDeleteDialog
      v-model:show-dialog="showDeleteDialog"
      :prompt-text="`Delete git reference to ${deleteTarget?.repoUrl}?`"
      @confirmed="confirmDelete"
    />
  </div>
</template>

<style scoped lang="scss"></style>
