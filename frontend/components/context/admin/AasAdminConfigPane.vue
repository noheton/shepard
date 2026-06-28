<script setup lang="ts">
import { useAasAdminConfig } from "~/composables/aas/useAasAdminConfig";
import { useAasRegistrations } from "~/composables/aas/useAasRegistrations";
import { useInstanceCapabilities } from "~/composables/context/useInstanceCapabilities";
import { AdminFragments } from "./adminMenuItems";

const { config, isLoading, isSaving, error, refresh, patch } = useAasAdminConfig();
const {
  registrationsPage,
  isLoading: isLoadingRegs,
  isSyncing,
  error: regsError,
  lastSyncResult,
  triggerSync,
} = useAasRegistrations();

const { isPluginEnabled } = useInstanceCapabilities();
const isPluginInstalled = computed(() => isPluginEnabled("aas"));

// IDTA template import (one-shot POST)
const isImporting = ref(false);
const importResult = ref<{ created: number; skipped: number } | null>(null);
const importError = ref<string | null>(null);

function v2BaseUrl(): string {
  const runtimeConfig = useRuntimeConfig().public;
  const explicit = runtimeConfig.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (runtimeConfig.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

async function importIdtaTemplates() {
  isImporting.value = true;
  importError.value = null;
  importResult.value = null;
  try {
    const { data: session } = useAuth();
    const accessToken = session.value?.accessToken;
    const response = await fetch(`${v2BaseUrl()}/v2/admin/aas/import-idta-templates`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        Accept: "application/json",
      },
    });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const result = await response.json();
    importResult.value = { created: result.created?.length ?? 0, skipped: result.skipped ?? 0 };
  } catch (e) {
    importError.value = "IDTA template import failed";
    handleError(e, "importing IDTA AAS templates");
  } finally {
    isImporting.value = false;
  }
}

function statusColor(status: string) {
  if (status === "SYNCED") return "success";
  if (status === "FAILED") return "error";
  return "warning";
}

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

const wellKnownUrl = computed(() => {
  const base = config.value?.baseUrl?.replace(/\/$/, "") ?? "";
  return base ? `${base}/v2/aas/.well-known/aas-server` : "";
});
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

          <template v-if="config.baseUrl">
            <v-divider />
            <div>
              <div class="text-body-2 font-weight-medium mb-1">Well-known URL</div>
              <div class="text-caption text-medium-emphasis mb-2">
                Share this URL with external IDTA registries so they can discover
                this instance's AAS capabilities.
              </div>
              <div class="d-flex align-center ga-1 flex-wrap">
                <code class="text-caption">{{ wellKnownUrl }}</code>
                <ClipboardButton :text="wellKnownUrl" success-message="Well-known URL copied" />
              </div>
            </div>
          </template>
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
      <!-- Registry Registrations Outbox -->
      <v-card variant="outlined">
        <v-card-title class="d-flex align-center ga-2 bg-surface-variant">
          <v-icon icon="mdi-sync" />
          <span>Registry Registrations</span>
          <v-chip
            v-if="registrationsPage"
            size="small"
            variant="tonal"
            class="ml-2"
          >
            {{ registrationsPage.total }} total
          </v-chip>
          <v-spacer />
          <v-btn
            size="small"
            variant="tonal"
            prepend-icon="mdi-sync"
            :loading="isSyncing"
            :disabled="isSyncing || !config.enabled || !config.registryUrl"
            @click="triggerSync"
          >
            Sync Now
          </v-btn>
        </v-card-title>

        <v-card-text class="pa-4 d-flex flex-column ga-3">
          <v-alert
            v-if="regsError"
            type="error"
            variant="tonal"
            density="compact"
            closable
            @click:close="regsError = null"
          >
            {{ regsError }}
          </v-alert>

          <v-alert
            v-if="lastSyncResult !== null"
            type="success"
            variant="tonal"
            density="compact"
          >
            Sync complete — {{ lastSyncResult.synced }} shell(s) registered.
          </v-alert>

          <centered-loading-spinner v-if="isLoadingRegs && !registrationsPage" />

          <template v-else-if="registrationsPage">
            <div
              v-if="registrationsPage.items.length === 0"
              class="text-body-2 text-medium-emphasis"
            >
              No registration rows yet. Rows are seeded when the AAS integration
              is enabled and the first sync runs.
            </div>
            <v-table v-else density="compact">
              <thead>
                <tr>
                  <th>Collection (Shell AppId)</th>
                  <th>Registry</th>
                  <th>Status</th>
                  <th>Last attempt</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="row in registrationsPage.items"
                  :key="row.appId"
                >
                  <td>
                    <code class="text-caption">{{ row.shellAppId }}</code>
                  </td>
                  <td>
                    <span class="text-caption">{{ row.registryUrl }}</span>
                  </td>
                  <td>
                    <v-chip
                      :color="statusColor(row.status)"
                      size="x-small"
                      variant="tonal"
                    >
                      {{ row.status }}
                    </v-chip>
                    <div
                      v-if="row.errorMessage"
                      class="text-caption text-error mt-1"
                    >
                      {{ row.errorMessage }}
                    </div>
                  </td>
                  <td>
                    <span class="text-caption text-medium-emphasis">
                      {{
                        row.lastAttemptAt
                          ? new Date(row.lastAttemptAt).toLocaleString()
                          : "—"
                      }}
                    </span>
                  </td>
                </tr>
              </tbody>
            </v-table>
          </template>
        </v-card-text>
      </v-card>

      <!-- IDTA Submodel Templates -->
      <v-card variant="outlined">
        <v-card-title class="d-flex align-center ga-2 bg-surface-variant">
          <v-icon icon="mdi-package-variant-closed" />
          <span>IDTA Submodel Templates</span>
        </v-card-title>

        <v-card-text class="pa-4 d-flex flex-column ga-3">
          <div class="text-body-2 text-medium-emphasis">
            Import the bundled IDTA Submodel Templates (e.g. Contact Information,
            Nameplate, Technical Data) into the global template library.
            Safe to re-run — identical templates are skipped.
          </div>

          <v-alert
            v-if="importError"
            type="error"
            variant="tonal"
            density="compact"
            closable
            @click:close="importError = null"
          >
            {{ importError }}
          </v-alert>

          <v-alert
            v-if="importResult"
            type="success"
            variant="tonal"
            density="compact"
          >
            Import complete — {{ importResult.created }} created,
            {{ importResult.skipped }} skipped.
          </v-alert>

          <div class="d-flex justify-end">
            <v-btn
              size="small"
              variant="tonal"
              prepend-icon="mdi-download-outline"
              :loading="isImporting"
              :disabled="isImporting || !config.enabled"
              @click="importIdtaTemplates"
            >
              Import IDTA Templates
            </v-btn>
          </div>
        </v-card-text>
      </v-card>
    </template>
  </div>
</template>

<style scoped lang="scss"></style>
