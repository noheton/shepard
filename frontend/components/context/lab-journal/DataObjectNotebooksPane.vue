<script setup lang="ts">
import { useFetchNotebooks } from "~/composables/context/useFetchNotebooks";
import { useJupyterConfig } from "~/composables/context/admin/useJupyterConfig";

const props = defineProps<{ dataObjectAppId: string }>();
const emit = defineEmits(["numberOfEntriesChanged"]);

const { notebooks, isLoading } = useFetchNotebooks(props.dataObjectAppId);

// UX Pattern D: surface the count to the parent panel title (low-emphasis
// badge in the v-expansion-panel-title). Mirrors the lab-journal pattern.
watch(
  notebooks,
  list => emit("numberOfEntriesChanged", list?.length ?? 0),
  { immediate: true },
);

// task #240 (2026-05-30): the "Open in JupyterHub" affordance reads the
// admin-configured `:JupyterConfig` singleton via the public sister endpoint
// `/v2/jupyter/config`. The previous per-user `editor.preferredJupyter`
// setting was removed — operators set one instance-wide hub URL in the
// admin pane.
const { config: jupyterConfig } = useJupyterConfig();
const jupyterAffordanceVisible = computed(
  () =>
    !!jupyterConfig.value &&
    jupyterConfig.value.enabled === true &&
    !!jupyterConfig.value.hubUrl &&
    jupyterConfig.value.hubUrl.length > 0,
);

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

/**
 * Build the JupyterHub launch URL for a singleton FileReference appId.
 * Mirrors the convention in DataObjectDataReferencesTable.vue —
 * `{hubUrl}/hub/spawn?file={downloadUrl}`.
 *
 * Returns null when the affordance gate is closed.
 */
function jupyterLaunchUrl(appId: string): string | null {
  const cfg = jupyterConfig.value;
  if (!cfg || !cfg.enabled || !cfg.hubUrl) return null;
  const downloadUrl = `${v2BaseUrl()}/v2/files/${encodeURIComponent(appId)}/content`;
  const hubBase = cfg.hubUrl.replace(/\/$/, "");
  return `${hubBase}/hub/spawn?file=${encodeURIComponent(downloadUrl)}`;
}

function openInJupyter(appId: string) {
  const url = jupyterLaunchUrl(appId);
  if (!url) return;
  window.open(url, "_blank", "noopener,noreferrer");
}

function downloadUrl(appId: string): string {
  return `${v2BaseUrl()}/v2/files/${encodeURIComponent(appId)}/content`;
}

function formatBytes(bytes: number | null | undefined): string {
  if (bytes == null) return "—";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
</script>

<template>
  <div class="d-flex flex-column ga-4">
    <div class="d-flex align-center justify-space-between">
      <h5 class="text-h5">Jupyter Notebooks</h5>
    </div>

    <centered-loading-spinner v-if="isLoading" />

    <v-list v-else-if="notebooks.length > 0" lines="two">
      <v-list-item
        v-for="nb in notebooks"
        :key="nb.appId"
        class="px-0"
      >
        <template #prepend>
          <v-icon icon="mdi-notebook-outline" class="mr-2" />
        </template>

        <v-list-item-title>{{ nb.fileName }}</v-list-item-title>
        <v-list-item-subtitle>
          <v-chip
            size="x-small"
            :color="nb.referenceKind === 'SINGLETON' ? 'primary' : 'secondary'"
            variant="tonal"
            class="mr-1"
          >
            {{ nb.referenceKind === 'SINGLETON' ? 'Singleton' : 'Bundle file' }}
          </v-chip>
          <span v-if="nb.fileSize != null">{{ formatBytes(nb.fileSize) }}</span>
          <span v-if="nb.createdBy" class="ml-1">· {{ nb.createdBy }}</span>
        </v-list-item-subtitle>

        <template #append>
          <div class="d-flex ga-2 align-center">
            <!-- Download — SINGLETON only -->
            <v-btn
              v-if="nb.referenceKind === 'SINGLETON'"
              :href="downloadUrl(nb.appId)"
              target="_blank"
              rel="noopener noreferrer"
              variant="tonal"
              density="comfortable"
              prepend-icon="mdi-download-outline"
              size="small"
            >
              Download
            </v-btn>

            <!-- Open in JupyterHub — SINGLETON only, visible only when admin
                 has enabled JupyterHub and set a hub URL. -->
            <v-btn
              v-if="nb.referenceKind === 'SINGLETON' && jupyterAffordanceVisible"
              variant="flat"
              color="warning"
              density="comfortable"
              prepend-icon="mdi-jupyter"
              size="small"
              data-testid="notebook-open-in-jupyter"
              @click="openInJupyter(nb.appId)"
            >
              Open in JupyterHub
            </v-btn>
          </div>
        </template>
      </v-list-item>
    </v-list>

    <div v-else class="text-medium-emphasis">
      No Jupyter notebooks attached yet. Upload a <code>.ipynb</code> file as a
      File Reference to see it here.
    </div>
  </div>
</template>

<style scoped lang="scss"></style>
