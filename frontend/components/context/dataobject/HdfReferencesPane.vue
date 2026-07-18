<script setup lang="ts">
import { useFetchHdfContainerOptions } from "~/composables/context/useFetchHdfContainerOptions";

/**
 * A5c — per-DataObject HDF5 dataset reference panel.
 *
 * Displays the list of HdfReference nodes attached to the DataObject
 * identified by `dataObjectAppId` and allows creating / deleting them.
 *
 * API surface: /v2/data-objects/{dataObjectAppId}/hdf-references
 *   GET    → list
 *   POST   → create (body: { hdfContainerAppId, datasetPath, description? })
 *   DELETE /{referenceAppId} → remove
 *
 * Auth: HdfReference is not yet regenerated into @dlr-shepard/backend-client,
 * so raw fetch() calls are used here with an explicit Bearer token obtained
 * from useAuth() — the same pattern as useFetchVideoStreamReferences.ts.
 *
 * Opt-in: the backend returns 404 when shepard.hdf.enabled=false.
 * This panel handles that gracefully (empty state, no crash).
 */

interface HdfReference {
  appId: string;
  hdfContainerAppId?: string;
  datasetPath?: string;
  description?: string;
}

interface HdfReferenceCreateBody {
  hdfContainerAppId: string;
  datasetPath: string;
  description?: string;
}

const props = defineProps<{
  dataObjectAppId: string;
  canEdit?: boolean;
}>();

// ── Auth + base URL ────────────────────────────────────────────────────────
const { data: session } = useAuth();

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

function authHeaders(): Record<string, string> {
  const token = session.value?.accessToken;
  if (!token) return {};
  return { Authorization: `Bearer ${token}` };
}

// ── State ─────────────────────────────────────────────────────────────────
const references = ref<HdfReference[]>([]);
const isLoading = ref(false);
const loadError = ref<string | null>(null);
const isSaving = ref(false);
const saveError = ref<string | null>(null);

const showCreateDialog = ref(false);
const showDeleteDialog = ref(false);
const deleteTarget = ref<HdfReference | null>(null);

const showAnnotateDialog = ref(false);
const annotateTarget = ref<HdfReference | null>(null);

const createForm = reactive<HdfReferenceCreateBody>({
  hdfContainerAppId: "",
  datasetPath: "",
  description: undefined,
});

// UIRULE-NO-MANUAL-IDS: the "HDF Container" field is a searchable, permission-
// filtered picker over GET /v2/containers?kind=hdf — the user searches by name;
// the appId travels invisibly (item-value). Replaces the raw "paste the UUID v7
// of the HdfContainer" text field. The paste fallback survives one deprecation
// window behind an "advanced" toggle per the rule.
const {
  query: containerSearch,
  options: containerOptions,
  isLoading: containerOptionsLoading,
  refresh: refreshContainerOptions,
} = useFetchHdfContainerOptions();
const showAdvancedPaste = ref(false);

function toggleAdvancedPaste() {
  showAdvancedPaste.value = !showAdvancedPaste.value;
  // Reset the in-flight selection so a picked value and a pasted value never
  // silently coexist in the shared createForm.hdfContainerAppId model.
  createForm.hdfContainerAppId = "";
}

// ── Fetch ─────────────────────────────────────────────────────────────────
async function fetchReferences() {
  isLoading.value = true;
  loadError.value = null;
  const url = `${v2BaseUrl()}/v2/data-objects/${encodeURIComponent(props.dataObjectAppId)}/hdf-references`;
  try {
    const response = await fetch(url, {
      headers: { ...authHeaders(), Accept: "application/json" },
    });
    if (response.status === 404) {
      // HDF feature off or DataObject not found; treat as empty list
      references.value = [];
      return;
    }
    if (!response.ok) {
      loadError.value = `Failed to load HDF references (HTTP ${response.status}).`;
      return;
    }
    references.value = (await response.json()) as HdfReference[];
  } catch {
    loadError.value = "Failed to load HDF references.";
  } finally {
    isLoading.value = false;
  }
}

// ── Create ────────────────────────────────────────────────────────────────
function openCreate() {
  createForm.hdfContainerAppId = "";
  createForm.datasetPath = "";
  createForm.description = undefined;
  saveError.value = null;
  showAdvancedPaste.value = false;
  containerSearch.value = "";
  showCreateDialog.value = true;
  // Populate the picker options on open (search-as-you-type refines afterwards).
  refreshContainerOptions();
}

async function submitCreate() {
  if (!createForm.hdfContainerAppId || !createForm.datasetPath) return;
  isSaving.value = true;
  saveError.value = null;
  const url = `${v2BaseUrl()}/v2/data-objects/${encodeURIComponent(props.dataObjectAppId)}/hdf-references`;
  try {
    const response = await fetch(url, {
      method: "POST",
      headers: {
        ...authHeaders(),
        "Content-Type": "application/json",
        Accept: "application/json",
      },
      body: JSON.stringify({
        hdfContainerAppId: createForm.hdfContainerAppId,
        datasetPath: createForm.datasetPath,
        description: createForm.description || undefined,
      }),
    });
    if (!response.ok) {
      const bodyText = await response.text().catch(() => "");
      saveError.value = `Failed to create HDF reference (HTTP ${response.status}).${bodyText ? ` ${bodyText.slice(0, 120)}` : ""}`;
      return;
    }
    showCreateDialog.value = false;
    await fetchReferences();
  } catch {
    saveError.value = "Failed to create HDF reference.";
  } finally {
    isSaving.value = false;
  }
}

// ── Annotate ──────────────────────────────────────────────────────────────
function openAnnotate(ref: HdfReference) {
  annotateTarget.value = ref;
  showAnnotateDialog.value = true;
}

// ── Delete ────────────────────────────────────────────────────────────────
function openDelete(ref: HdfReference) {
  deleteTarget.value = ref;
  showDeleteDialog.value = true;
}

async function confirmDelete() {
  if (!deleteTarget.value) return;
  isSaving.value = true;
  saveError.value = null;
  const url = `${v2BaseUrl()}/v2/data-objects/${encodeURIComponent(props.dataObjectAppId)}/hdf-references/${encodeURIComponent(deleteTarget.value.appId)}`;
  try {
    const response = await fetch(url, {
      method: "DELETE",
      headers: authHeaders(),
    });
    if (!response.ok && response.status !== 204) {
      saveError.value = `Failed to delete HDF reference (HTTP ${response.status}).`;
      return;
    }
    showDeleteDialog.value = false;
    await fetchReferences();
  } catch {
    saveError.value = "Failed to delete HDF reference.";
  } finally {
    isSaving.value = false;
  }
}

// ── Lifecycle ─────────────────────────────────────────────────────────────
onMounted(fetchReferences);
</script>

<template>
  <div class="d-flex flex-column ga-4">
    <div class="d-flex align-center justify-space-between">
      <h5 class="text-h5">
        HDF5 Dataset References
        <span
          class="text-low-emphasis ml-1"
          data-testid="hdf-references-count"
        >({{ references.length }})</span>
      </h5>
      <v-btn
        v-if="canEdit"
        color="primary"
        variant="flat"
        prepend-icon="mdi-plus-circle"
        @click="openCreate"
      >
        Add reference
      </v-btn>
    </div>

    <v-alert v-if="loadError" type="error" closable>{{ loadError }}</v-alert>
    <v-alert v-if="saveError" type="error" closable>{{ saveError }}</v-alert>

    <centered-loading-spinner v-if="isLoading" />

    <v-table v-else-if="references.length > 0">
      <thead>
        <tr>
          <th>Container</th>
          <th>Dataset Path</th>
          <th>Description</th>
          <th />
        </tr>
      </thead>
      <tbody>
        <tr v-for="ref in references" :key="ref.appId">
          <td>
            <code>{{ ref.hdfContainerAppId ?? "—" }}</code>
          </td>
          <td>
            <code>{{ ref.datasetPath ?? "—" }}</code>
          </td>
          <td>{{ ref.description ?? "—" }}</td>
          <td class="text-right">
            <v-btn
              icon="mdi-tag-outline"
              variant="plain"
              density="compact"
              color="secondary"
              :data-testid="`hdf-ref-annotate-${ref.appId}`"
              title="Annotate this reference"
              @click="openAnnotate(ref)"
            />
            <v-btn
              v-if="canEdit"
              icon="mdi-delete-outline"
              variant="plain"
              density="compact"
              color="error"
              :data-testid="`hdf-ref-delete-${ref.appId}`"
              @click="openDelete(ref)"
            />
          </td>
        </tr>
      </tbody>
    </v-table>

    <div v-else class="text-medium-emphasis">No HDF5 dataset references linked yet.</div>

    <!-- Create dialog -->
    <FormDialog
      v-model:show-dialog="showCreateDialog"
      title="Add HDF5 Dataset Reference"
      :loading="isSaving"
      :submit-disabled="!createForm.hdfContainerAppId || !createForm.datasetPath || isSaving"
      save-button-text="Add"
      @submit="submitCreate"
    >
      <template #form>
        <v-row class="pt-4">
          <v-col cols="12">
            <!-- UIRULE-NO-MANUAL-IDS: searchable, permission-filtered picker over
                 GET /v2/containers?kind=hdf — the user searches an HDF container by
                 name; its appId travels invisibly (item-value). Replaces the raw
                 "paste the UUID v7 of the HdfContainer" text field. -->
            <v-autocomplete
              v-if="!showAdvancedPaste"
              v-model="createForm.hdfContainerAppId"
              v-model:search="containerSearch"
              :items="containerOptions"
              :loading="containerOptionsLoading"
              item-title="name"
              item-value="appId"
              label="HDF Container"
              hint="Search an HDF container you can read; its appId is stored automatically."
              persistent-hint
              clearable
              auto-select-first
              spellcheck="false"
              placeholder="search an HDF container by name"
              no-data-text="No matching HDF containers you can read"
              data-testid="hdf-container-autocomplete"
            />
            <!-- Advanced escape hatch (one deprecation window): paste an appId
                 directly. The picker above is the default per the "user never
                 types an ID" rule; this survives only until the picker is proven
                 to cover every case an operator needs. -->
            <v-text-field
              v-else
              v-model="createForm.hdfContainerAppId"
              label="HDF Container appId"
              placeholder="UUID v7 of the HdfContainer"
              hint="The appId of the HdfContainer this reference points into."
              persistent-hint
              spellcheck="false"
              data-testid="hdf-container-paste"
            />
            <v-btn
              variant="text"
              size="x-small"
              density="compact"
              class="mt-1"
              :prepend-icon="showAdvancedPaste ? 'mdi-chevron-up' : 'mdi-chevron-down'"
              data-testid="hdf-container-advanced-toggle"
              @click="toggleAdvancedPaste"
            >
              {{ showAdvancedPaste ? "Pick from a list instead" : "Advanced: paste an appId" }}
            </v-btn>
          </v-col>
          <v-col cols="12">
            <v-text-field
              v-model="createForm.datasetPath"
              label="Dataset Path"
              placeholder="/sensor_data/channel_A"
              hint="HDF5 dataset path within the container."
              persistent-hint
              required
            />
          </v-col>
          <v-col cols="12">
            <v-text-field
              v-model="createForm.description"
              label="Description"
              placeholder="Optional description"
              clearable
            />
          </v-col>
        </v-row>
      </template>
    </FormDialog>

    <!-- Delete confirm -->
    <ConfirmDeleteDialog
      v-model:show-dialog="showDeleteDialog"
      :prompt-text="`Delete HDF reference to ${deleteTarget?.datasetPath}?`"
      @confirmed="confirmDelete"
    />

    <!-- Annotate dialog (A5c-annotation) -->
    <AnnotationDialog
      v-if="showAnnotateDialog"
      v-model:show-dialog="showAnnotateDialog"
      :subject-app-id="annotateTarget?.appId"
      subject-kind="HdfReference"
    />
  </div>
</template>

<style scoped lang="scss">
</style>
