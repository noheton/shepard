<script setup lang="ts">
import {
  useOntologyBundles,
  type OntologyBundleIO,
  type UploadOntologyMetadata,
} from "~/composables/context/admin/useOntologyBundles";
import { AdminFragments } from "./adminMenuItems";

const {
  bundles,
  isLoading,
  isActing,
  fetchError,
  actionError,
  refresh,
  setEnabled,
  uploadBundle,
  deleteBundle,
} = useOntologyBundles();

// ── enable / disable toggle ──────────────────────────────────────────────────
const togglingId = ref<string | null>(null);

async function onToggle(bundle: OntologyBundleIO, newValue: boolean) {
  togglingId.value = bundle.id;
  try {
    await setEnabled(bundle.id, newValue);
    emitSuccess(
      `Bundle "${bundle.id}" ${newValue ? "enabled" : "disabled"}. Changes take effect on next backend startup.`,
    );
  } finally {
    togglingId.value = null;
  }
}

// ── delete ───────────────────────────────────────────────────────────────────
const deletingBundle = ref<OntologyBundleIO | null>(null);
const showDeleteDialog = ref(false);

function confirmDelete(bundle: OntologyBundleIO) {
  deletingBundle.value = bundle;
  showDeleteDialog.value = true;
}

async function onDeleteConfirmed() {
  if (!deletingBundle.value) return;
  const id = deletingBundle.value.id;
  try {
    await deleteBundle(id);
    emitSuccess(`Bundle "${id}" removed.`);
  } catch {
    // actionError is already set; surface handled below
  } finally {
    showDeleteDialog.value = false;
    deletingBundle.value = null;
  }
}

// ── upload ───────────────────────────────────────────────────────────────────
const showUploadDialog = ref(false);
const uploadFile = ref<File | null>(null);
const uploadMeta = ref<UploadOntologyMetadata>({
  id: "",
  name: "",
  iriPrefix: "",
  canonicalUrl: "",
  license: "",
});
const uploadError = ref<string | null>(null);
const isUploading = ref(false);

const uploadFileInput = ref<HTMLInputElement | null>(null);

function openUploadDialog() {
  uploadFile.value = null;
  uploadMeta.value = { id: "", name: "", iriPrefix: "", canonicalUrl: "", license: "" };
  uploadError.value = null;
  showUploadDialog.value = true;
}

function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement;
  uploadFile.value = input.files?.[0] ?? null;
}

async function submitUpload() {
  if (!uploadFile.value || !uploadMeta.value.id) {
    uploadError.value = "File and bundle ID are required.";
    return;
  }
  uploadError.value = null;
  isUploading.value = true;
  try {
    const meta: UploadOntologyMetadata = {
      id: uploadMeta.value.id.trim(),
      name: uploadMeta.value.name?.trim() || undefined,
      iriPrefix: uploadMeta.value.iriPrefix?.trim() || undefined,
      canonicalUrl: uploadMeta.value.canonicalUrl?.trim() || undefined,
      license: uploadMeta.value.license?.trim() || undefined,
    };
    await uploadBundle(uploadFile.value, meta);
    emitSuccess(`Bundle "${meta.id}" uploaded.`);
    showUploadDialog.value = false;
  } catch (e) {
    uploadError.value =
      e instanceof Error ? e.message : "Upload failed";
  } finally {
    isUploading.value = false;
  }
}

// ── helpers ──────────────────────────────────────────────────────────────────
function formatBytes(bytes: number): string {
  if (bytes === 0) return "0 B";
  const k = 1024;
  const sizes = ["B", "KB", "MB"];
  const i = Math.min(Math.floor(Math.log(bytes) / Math.log(k)), sizes.length - 1);
  return `${(bytes / Math.pow(k, i)).toFixed(1)} ${sizes[i]}`;
}

function sourceChipColor(source: string): string {
  return source === "builtin" ? "primary" : "secondary";
}

const headers = [
  { title: "ID", key: "id", sortable: true, width: "20%" },
  { title: "Name", key: "name", sortable: true },
  { title: "Source", key: "source", sortable: true, width: "100px" },
  { title: "Size", key: "byteSize", sortable: true, width: "90px" },
  { title: "Enabled", key: "enabled", sortable: true, width: "110px" },
  { title: "", key: "actions", sortable: false, width: "60px" },
];
</script>

<template>
  <div :id="AdminFragments.ONTOLOGY_BUNDLES" class="d-flex flex-column ga-4">
    <div class="d-flex align-center justify-space-between flex-wrap ga-2">
      <h4 class="text-h4">Ontology Bundles</h4>
      <div class="d-flex ga-2">
        <v-btn
          variant="text"
          size="small"
          prepend-icon="mdi-refresh"
          :loading="isLoading"
          @click="refresh"
        >
          Refresh
        </v-btn>
        <v-btn
          variant="tonal"
          color="primary"
          size="small"
          prepend-icon="mdi-upload-outline"
          @click="openUploadDialog"
        >
          Upload ontology
        </v-btn>
      </div>
    </div>

    <v-alert
      type="info"
      variant="tonal"
      density="compact"
      icon="mdi-information-outline"
    >
      Built-in bundles ship with Shepard and update via release upgrades.
      Enable / disable changes take effect on the next backend startup (bundle
      seeding runs at boot time). Required bundles cannot be disabled.
    </v-alert>

    <v-alert
      v-if="fetchError"
      type="error"
      variant="tonal"
      closable
      @click:close="fetchError = null"
    >
      {{ fetchError }}
    </v-alert>

    <v-alert
      v-if="actionError"
      type="error"
      variant="tonal"
      closable
      @click:close="actionError = null"
    >
      {{ actionError }}
    </v-alert>

    <centered-loading-spinner v-if="isLoading && bundles.length === 0" />

    <EmptyListIcon
      v-else-if="!isLoading && bundles.length === 0"
      label="No ontology bundles found."
    />

    <DataTable
      v-else
      :headers="headers"
      :items="bundles"
      :loading="isLoading"
      :items-per-page="-1"
      :hide-default-footer="true"
      :cell-props="{ class: 'text-textbody1' }"
      :header-props="{
        class: 'text-subtitle-2 text-textbody1',
        style: 'background-color: rgb(var(--v-theme-divider2))',
      }"
    >
      <!-- ID column -->
      <template #[`item.id`]="{ item }: { item: OntologyBundleIO }">
        <div class="d-flex flex-column">
          <span class="text-textbody1 font-weight-medium">{{ item.id }}</span>
          <span
            v-if="item.iriPrefix"
            class="text-caption text-medium-emphasis"
          >
            {{ item.iriPrefix }}
          </span>
        </div>
      </template>

      <!-- Name column -->
      <template #[`item.name`]="{ item }: { item: OntologyBundleIO }">
        <div class="d-flex flex-column">
          <span class="text-textbody1">{{ item.name || "—" }}</span>
          <span v-if="item.license" class="text-caption text-medium-emphasis">
            <v-icon size="x-small" icon="mdi-scale-balance" />
            {{ item.license }}
          </span>
        </div>
      </template>

      <!-- Source chip -->
      <template #[`item.source`]="{ item }: { item: OntologyBundleIO }">
        <div class="d-flex align-center ga-1">
          <v-chip
            :color="sourceChipColor(item.source)"
            size="x-small"
            variant="tonal"
          >
            {{ item.source }}
          </v-chip>
          <v-chip
            v-if="item.required"
            color="warning"
            size="x-small"
            variant="tonal"
          >
            required
          </v-chip>
        </div>
      </template>

      <!-- Size -->
      <template #[`item.byteSize`]="{ item }: { item: OntologyBundleIO }">
        <span class="text-textbody2">{{ formatBytes(item.byteSize) }}</span>
      </template>

      <!-- Enable / disable toggle -->
      <template #[`item.enabled`]="{ item }: { item: OntologyBundleIO }">
        <v-progress-circular
          v-if="togglingId === item.id"
          indeterminate
          size="20"
          aria-label="Updating"
        />
        <v-switch
          v-else
          :model-value="item.enabled"
          color="primary"
          hide-details
          density="compact"
          :disabled="item.required || isActing"
          :title="item.required ? 'This bundle is required and cannot be disabled' : ''"
          @update:model-value="(val) => onToggle(item, val as boolean)"
        />
      </template>

      <!-- Actions -->
      <template #[`item.actions`]="{ item }: { item: OntologyBundleIO }">
        <ActionContainer>
          <ActionButton
            v-if="item.source === 'user'"
            icon="mdi-delete-outline"
            :disabled="isActing"
            @click="confirmDelete(item)"
          />
          <v-tooltip
            v-else
            text="Built-in bundles cannot be removed"
            location="start"
          >
            <template #activator="{ props: tooltipProps }">
              <v-btn
                v-bind="tooltipProps"
                icon="mdi-delete-outline"
                variant="text"
                size="small"
                disabled
              />
            </template>
          </v-tooltip>
        </ActionContainer>
      </template>

      <template #bottom>
        <v-divider :thickness="8" color="divider2" opacity="1" />
      </template>
    </DataTable>

    <!-- ── Delete confirmation dialog ──────────────────────────────────────── -->
    <ConfirmDeleteDialog
      v-if="showDeleteDialog && deletingBundle"
      v-model:show-dialog="showDeleteDialog"
      :prompt-text="`Remove bundle '${deletingBundle?.id}'? This cannot be undone.`"
      @confirmed="onDeleteConfirmed"
    />

    <!-- ── Upload dialog ─────────────────────────────────────────────────────── -->
    <v-dialog v-model="showUploadDialog" max-width="560" persistent>
      <v-card class="bg-canvas">
        <v-card-title class="text-h5 pa-4">
          Upload ontology bundle
        </v-card-title>

        <v-card-text class="d-flex flex-column ga-3">
          <v-alert
            v-if="uploadError"
            type="error"
            variant="tonal"
            density="compact"
          >
            {{ uploadError }}
          </v-alert>

          <v-text-field
            v-model="uploadMeta.id"
            label="Bundle ID *"
            hint="Lowercase slug, e.g. my-ontology. Must be unique."
            persistent-hint
            :rules="[(v: string) => !!v || 'Required', (v: string) => /^[a-z0-9][a-z0-9_-]{0,63}$/.test(v) || 'Must match ^[a-z0-9][a-z0-9_-]{0,63}$']"
            density="compact"
            variant="outlined"
          />

          <v-text-field
            v-model="uploadMeta.name"
            label="Human-readable name"
            density="compact"
            variant="outlined"
          />

          <v-text-field
            v-model="uploadMeta.iriPrefix"
            label="IRI prefix"
            hint="E.g. https://example.org/my-ontology#"
            persistent-hint
            density="compact"
            variant="outlined"
          />

          <v-text-field
            v-model="uploadMeta.canonicalUrl"
            label="Canonical URL (for future refresh)"
            density="compact"
            variant="outlined"
          />

          <v-text-field
            v-model="uploadMeta.license"
            label="License (SPDX)"
            hint="E.g. CC-BY-4.0"
            persistent-hint
            density="compact"
            variant="outlined"
          />

          <div>
            <p class="text-body-2 mb-2">
              Turtle file (.ttl) <span class="text-error">*</span>
              <span class="text-caption text-medium-emphasis ml-1">(max 10 MB)</span>
            </p>
            <input
              ref="uploadFileInput"
              type="file"
              accept=".ttl,text/turtle"
              style="display: none"
              @change="onFileChange"
            >
            <div class="d-flex align-center ga-2">
              <v-btn
                variant="tonal"
                size="small"
                prepend-icon="mdi-file-upload-outline"
                @click="uploadFileInput?.click()"
              >
                Choose file
              </v-btn>
              <span class="text-body-2 text-medium-emphasis">
                {{ uploadFile?.name ?? "No file chosen" }}
              </span>
            </div>
          </div>
        </v-card-text>

        <v-card-actions class="pa-4 pt-0">
          <v-spacer />
          <v-btn
            variant="flat"
            color="treeview"
            :disabled="isUploading"
            @click="showUploadDialog = false"
          >
            Cancel
          </v-btn>
          <v-btn
            variant="flat"
            color="primary"
            :loading="isUploading"
            :disabled="!uploadFile || !uploadMeta.id"
            @click="submitUpload"
          >
            Upload
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<style scoped lang="scss"></style>
