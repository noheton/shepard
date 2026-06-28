<script setup lang="ts">
import { useAasAdminConfig } from "~/composables/aas/useAasAdminConfig";
import { useInstanceCapabilities } from "~/composables/context/useInstanceCapabilities";
import { AdminFragments } from "./adminMenuItems";

const { config, isLoading, isSaving, error, refresh, patch } = useAasAdminConfig();

const { isPluginEnabled } = useInstanceCapabilities();
const isPluginInstalled = computed(() => isPluginEnabled("aas"));

// edit form state
const editRegistryUrl = ref("");
const editBaseUrl = ref("");
const editApiKey = ref("");
const showApiKey = ref(false);

watch(config, val => {
  if (!val) return;
  editRegistryUrl.value = val.registryUrl ?? "";
  editBaseUrl.value = val.baseUrl ?? "";
}, { immediate: true });

async function toggleEnabled(value: boolean) {
  await patch({ enabled: value });
}

async function saveRegistryUrl() {
  const url = editRegistryUrl.value.trim() || null;
  await patch({ registryUrl: url });
}

async function saveBaseUrl() {
  const url = editBaseUrl.value.trim() || null;
  await patch({ baseUrl: url });
}

async function saveApiKey() {
  const key = editApiKey.value.trim() || null;
  await patch({ registryApiKey: key });
  if (key !== null) editApiKey.value = "";
}

async function clearApiKey() {
  await patch({ registryApiKey: null });
}
</script>

<template>
  <div :id="AdminFragments.AAS_CONFIG" class="d-flex flex-column ga-4">
    <div class="d-flex align-center justify-space-between flex-wrap ga-2">
      <h4 class="text-h4">AAS Integration (IDTA Asset Administration Shell)</h4>
      <v-btn
        variant="text"
        size="small"
        prepend-icon="mdi-refresh"
        :loading="isLoading"
        @click="refresh"
      >
        Refresh
      </v-btn>
    </div>

    <v-alert
      v-if="!isPluginInstalled"
      type="info"
      variant="tonal"
      density="compact"
      icon="mdi-puzzle-outline"
    >
      The AAS plugin is not currently enabled on this instance. Place the plugin
      JAR in the plugins directory and restart the backend, then enable it under
      <strong>Plugins</strong>.
    </v-alert>

    <v-alert
      v-if="error"
      type="error"
      variant="tonal"
      closable
      @click:close="error = null"
    >
      {{ error }}
    </v-alert>

    <centered-loading-spinner v-if="isLoading && !config" />

    <template v-else-if="config">
      <!-- Master toggle -->
      <v-card variant="outlined">
        <v-card-title class="d-flex align-center ga-2 bg-surface-variant">
          <v-icon icon="mdi-layers-triple-outline" />
          <span>Integration Status</span>
          <v-chip
            :color="config.enabled ? 'success' : 'default'"
            size="small"
            variant="tonal"
            class="ml-2"
          >
            {{ config.enabled ? "Enabled" : "Disabled" }}
          </v-chip>
        </v-card-title>

        <v-card-text class="pa-4">
          <div class="d-flex align-center justify-space-between flex-wrap ga-2">
            <div>
              <div class="text-body-2 font-weight-medium">Integration enabled</div>
              <div class="text-caption text-medium-emphasis">
                When disabled, all <code>GET /v2/aas/shells</code> endpoints
                return 501. The IDTA Registry sync is also paused.
              </div>
            </div>
            <v-switch
              :model-value="config.enabled"
              color="primary"
              hide-details
              density="compact"
              :loading="isSaving"
              :disabled="isSaving"
              @update:model-value="(v) => toggleEnabled(v as boolean)"
            />
          </div>
        </v-card-text>
      </v-card>

      <!-- Registry URL -->
      <v-card variant="outlined">
        <v-card-title class="d-flex align-center ga-2 bg-surface-variant">
          <v-icon icon="mdi-server-network-outline" />
          <span>IDTA Registry</span>
        </v-card-title>

        <v-card-text class="pa-4 d-flex flex-column ga-4">
          <v-text-field
            v-model="editRegistryUrl"
            label="Registry URL"
            placeholder="https://registry.example.org/api/v3.0"
            hint="Base URL of the IDTA AAS Registry v3 REST API. Leave blank to disable registry sync."
            persistent-hint
            density="compact"
            variant="outlined"
            clearable
            :disabled="isSaving || !config.enabled"
            @keydown.enter="saveRegistryUrl"
          />
          <div class="d-flex justify-end">
            <v-btn
              size="small"
              variant="tonal"
              prepend-icon="mdi-content-save-outline"
              :loading="isSaving"
              :disabled="isSaving || !config.enabled"
              @click="saveRegistryUrl"
            >
              Save Registry URL
            </v-btn>
          </div>

          <v-divider />

          <!-- API key -->
          <div>
            <div class="text-body-2 font-weight-medium mb-1">Registry API key</div>
            <div class="text-caption text-medium-emphasis mb-3">
              <template v-if="config.apiKeyPresent">
                <v-icon size="small" color="success" icon="mdi-key-variant" />
                API key is set. Enter a new value to rotate; clear to remove.
              </template>
              <template v-else>
                No API key set (open registry or not needed).
              </template>
            </div>
            <v-text-field
              v-model="editApiKey"
              label="API key (write-only)"
              :type="showApiKey ? 'text' : 'password'"
              density="compact"
              variant="outlined"
              :append-inner-icon="showApiKey ? 'mdi-eye-off' : 'mdi-eye'"
              :disabled="isSaving || !config.enabled"
              @click:append-inner="showApiKey = !showApiKey"
              @keydown.enter="saveApiKey"
            />
            <div class="d-flex ga-2 justify-end">
              <v-btn
                v-if="config.apiKeyPresent"
                size="small"
                variant="text"
                color="error"
                prepend-icon="mdi-key-remove"
                :loading="isSaving"
                :disabled="isSaving || !config.enabled"
                @click="clearApiKey"
              >
                Remove key
              </v-btn>
              <v-btn
                size="small"
                variant="tonal"
                prepend-icon="mdi-key-plus"
                :loading="isSaving"
                :disabled="isSaving || !config.enabled || !editApiKey"
                @click="saveApiKey"
              >
                Set API key
              </v-btn>
            </div>
          </div>
        </v-card-text>
      </v-card>

      <!-- Base URL -->
      <v-card variant="outlined">
        <v-card-title class="d-flex align-center ga-2 bg-surface-variant">
          <v-icon icon="mdi-web" />
          <span>Instance Base URL</span>
        </v-card-title>

        <v-card-text class="pa-4 d-flex flex-column ga-4">
          <v-text-field
            v-model="editBaseUrl"
            label="Base URL"
            placeholder="https://shepard.example.org"
            hint="Public Shepard URL embedded in AAS Shell descriptors as the asset href. Used only when AAS is enabled."
            persistent-hint
            density="compact"
            variant="outlined"
            clearable
            :disabled="isSaving || !config.enabled"
            @keydown.enter="saveBaseUrl"
          />
          <div class="d-flex justify-end">
            <v-btn
              size="small"
              variant="tonal"
              prepend-icon="mdi-content-save-outline"
              :loading="isSaving"
              :disabled="isSaving || !config.enabled"
              @click="saveBaseUrl"
            >
              Save Base URL
            </v-btn>
          </div>
        </v-card-text>

        <v-card-actions class="pa-4 pt-0">
          <v-btn
            size="small"
            variant="text"
            prepend-icon="mdi-open-in-new"
            :href="`${$config.public.backendV2ApiUrl || ($config.public.backendApiUrl as string).replace(/\/shepard\/api\/?$/, '')}/v2/admin/config/aas`"
            target="_blank"
            rel="noopener noreferrer"
          >
            Open API config
          </v-btn>
        </v-card-actions>
      </v-card>
    </template>
  </div>
</template>

<style scoped lang="scss"></style>
