<script setup lang="ts">
import { useFetchNotebooks } from "~/composables/context/useFetchNotebooks";
import { useJupyterPreference } from "~/composables/context/useJupyterPreference";

const props = defineProps<{ dataObjectAppId: string }>();

const { notebooks, isLoading } = useFetchNotebooks(props.dataObjectAppId);
const { preferredJupyterUrl, isSaving, save } = useJupyterPreference();

const jupyterUrlInput = ref("");
const showUrlField = ref(false);

watch(
  preferredJupyterUrl,
  url => {
    jupyterUrlInput.value = url;
    if (!url) showUrlField.value = true;
  },
  { immediate: true },
);

function openInJupyter() {
  if (!preferredJupyterUrl.value) {
    showUrlField.value = true;
    return;
  }
  const base = preferredJupyterUrl.value.replace(/\/$/, "");
  window.open(base, "_blank", "noopener,noreferrer");
}

function downloadUrl(appId: string): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  const base =
    explicit && explicit.length > 0
      ? explicit.replace(/\/$/, "")
      : (config.backendApiUrl as string)
          .replace(/\/shepard\/api\/?$/, "")
          .replace(/\/$/, "");
  return `${base}/v2/files/${encodeURIComponent(appId)}/content`;
}

async function saveJupyterUrl() {
  await save(jupyterUrlInput.value.trim());
  if (preferredJupyterUrl.value) {
    showUrlField.value = false;
  }
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
      <v-btn
        v-if="!showUrlField"
        variant="tonal"
        prepend-icon="mdi-cog-outline"
        density="comfortable"
        @click="showUrlField = true"
      >
        JupyterHub URL
      </v-btn>
    </div>

    <!-- JupyterHub URL setting -->
    <v-expand-transition>
      <div v-if="showUrlField">
        <v-text-field
          v-model="jupyterUrlInput"
          label="Your JupyterHub base URL"
          placeholder="https://myhub.example.com"
          hint="Set your JupyterHub URL to enable 'Open in JupyterHub' buttons. Stored in your user preferences."
          persistent-hint
          density="comfortable"
          variant="outlined"
          :loading="isSaving"
          :disabled="isSaving"
          clearable
        >
          <template #append>
            <v-btn
              color="primary"
              variant="flat"
              density="comfortable"
              :loading="isSaving"
              :disabled="isSaving"
              @click="saveJupyterUrl"
            >
              Save
            </v-btn>
          </template>
        </v-text-field>
      </div>
    </v-expand-transition>

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

            <!-- Open in JupyterHub — SINGLETON only -->
            <template v-if="nb.referenceKind === 'SINGLETON'">
              <v-tooltip
                v-if="!preferredJupyterUrl"
                text="Set your JupyterHub URL above to enable this button"
                location="top"
              >
                <template #activator="{ props: tooltipProps }">
                  <span v-bind="tooltipProps">
                    <v-btn
                      variant="flat"
                      color="warning"
                      density="comfortable"
                      prepend-icon="mdi-jupyter"
                      size="small"
                      disabled
                    >
                      Open in JupyterHub
                    </v-btn>
                  </span>
                </template>
              </v-tooltip>
              <v-btn
                v-else
                variant="flat"
                color="warning"
                density="comfortable"
                prepend-icon="mdi-jupyter"
                size="small"
                @click="openInJupyter()"
              >
                Open in JupyterHub
              </v-btn>
            </template>
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
