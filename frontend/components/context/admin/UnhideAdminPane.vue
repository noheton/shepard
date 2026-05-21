<script setup lang="ts">
import { useUnhideAdminConfig } from "~/composables/context/admin/useUnhideAdminConfig";
import { useInstanceCapabilities } from "~/composables/context/useInstanceCapabilities";
import { AdminFragments } from "./adminMenuItems";

const { config, isLoading, isSaving, error, refresh, patch } =
  useUnhideAdminConfig();

const { isPluginEnabled } = useInstanceCapabilities();
const isPluginInstalled = computed(() => isPluginEnabled("unhide"));

async function toggleEnabled(value: boolean) {
  await patch({ enabled: value });
}

async function toggleFeedPublic(value: boolean) {
  await patch({ feedPublic: value });
}

function formatDate(iso: string | null | undefined): string {
  if (!iso) return "—";
  try {
    return new Date(iso).toLocaleString();
  } catch {
    return iso;
  }
}
</script>

<template>
  <div :id="AdminFragments.UNHIDE" class="d-flex flex-column ga-4">
    <div class="d-flex align-center justify-space-between flex-wrap ga-2">
      <h4 class="text-h4">Unhide (Helmholtz Knowledge Graph)</h4>
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
      The Unhide plugin is not currently enabled on this instance. Place the
      plugin JAR in the plugins directory and restart the backend, then enable
      it under <strong>Plugins</strong>.
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
      <v-card variant="outlined">
        <v-card-title class="d-flex align-center ga-2 bg-surface-variant">
          <v-icon icon="mdi-web-sync" />
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

        <v-card-text class="pa-4 d-flex flex-column ga-4">
          <div class="d-flex align-center justify-space-between flex-wrap ga-2">
            <div>
              <div class="text-body-2 font-weight-medium">Integration enabled</div>
              <div class="text-caption text-medium-emphasis">
                When disabled, the harvest feed returns 503 and no collections
                are exposed to the Helmholtz Knowledge Graph.
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

          <v-divider />

          <div class="d-flex align-center justify-space-between flex-wrap ga-2">
            <div>
              <div class="text-body-2 font-weight-medium">Feed public</div>
              <div class="text-caption text-medium-emphasis">
                When off, Unhide's harvester must supply the X-API-KEY header.
                Use the CLI to rotate the harvest key:
                <code>shepard-admin unhide rotate-harvest-key</code>
              </div>
            </div>
            <v-switch
              :model-value="config.feedPublic"
              color="primary"
              hide-details
              density="compact"
              :loading="isSaving"
              :disabled="isSaving || !config.enabled"
              @update:model-value="(v) => toggleFeedPublic(v as boolean)"
            />
          </div>

          <v-divider />

          <v-list density="compact" lines="two">
            <v-list-item v-if="config.contactEmail">
              <v-list-item-title class="text-caption text-medium-emphasis">
                Contact email
              </v-list-item-title>
              <v-list-item-subtitle class="text-body-2 mt-1">
                {{ config.contactEmail }}
              </v-list-item-subtitle>
            </v-list-item>

            <v-list-item>
              <v-list-item-title class="text-caption text-medium-emphasis">
                Harvest API key fingerprint
              </v-list-item-title>
              <v-list-item-subtitle class="text-body-2 mt-1">
                <template v-if="config.harvestApiKeyFingerprint">
                  <code>{{ config.harvestApiKeyFingerprint }}…</code>
                  <span class="text-medium-emphasis ml-2 text-caption">
                    (minted {{ formatDate(config.harvestApiKeyMintedAt) }})
                  </span>
                </template>
                <span v-else class="text-medium-emphasis">No harvest key minted</span>
              </v-list-item-subtitle>
            </v-list-item>
          </v-list>
        </v-card-text>

        <v-card-actions class="pa-4 pt-0">
          <v-btn
            size="small"
            variant="tonal"
            prepend-icon="mdi-open-in-new"
            :href="`${$config.public.backendV2ApiUrl || ($config.public.backendApiUrl as string).replace(/\/shepard\/api\/?$/, '')}/v2/admin/unhide/config`"
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
