<script setup lang="ts">
import {
  useFetchPlugins,
  type PluginEntryIO,
} from "~/composables/context/admin/useFetchPlugins";
import { AdminFragments } from "./adminMenuItems";

const { plugins, isLoading, fetchError, refresh, togglePlugin } =
  useFetchPlugins();

const togglingId = ref<string | null>(null);
const toggleError = ref<string | null>(null);

async function onToggle(plugin: PluginEntryIO, newValue: boolean) {
  toggleError.value = null;
  togglingId.value = plugin.id;
  try {
    await togglePlugin(plugin.id, newValue);
  } catch (e) {
    const message =
      e instanceof Error ? e.message : `Failed to update "${plugin.id}"`;
    toggleError.value = message;
    handleError(e, `patching plugin "${plugin.id}"`);
    await refresh();
  } finally {
    togglingId.value = null;
  }
}

function stateColor(state: PluginEntryIO["state"]): string {
  switch (state) {
    case "ENABLED":
      return "success";
    case "DISABLED":
      return "default";
    case "FAILED":
      return "error";
    case "DEGRADED":
      return "warning";
    case "DISCOVERED":
      return "info";
  }
}
</script>

<template>
  <div :id="AdminFragments.PLUGINS" class="d-flex flex-column ga-4">
    <h4 class="text-h4">Plugins</h4>

    <v-alert type="warning" variant="tonal" density="compact" icon="mdi-information-outline">
      Plugin changes take full effect on next backend restart. Enabling or
      disabling a running plugin may cause partial behaviour until restart.
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
      v-if="toggleError"
      type="error"
      closable
      @click:close="toggleError = null"
    >
      {{ toggleError }}
    </v-alert>

    <centered-loading-spinner v-if="isLoading && plugins.length === 0" />

    <EmptyListIcon
      v-else-if="!isLoading && plugins.length === 0"
      label="No plugins discovered. Place plugin JARs in the configured plugins directory and restart."
    />

    <template v-else>
      <v-card
        v-for="plugin in plugins"
        :key="plugin.id"
        variant="outlined"
      >
        <v-card-title class="d-flex align-center flex-wrap ga-2 pt-4 pb-2">
          <span class="text-body-1 font-weight-bold">
            {{ plugin.title || plugin.id }}
          </span>

          <v-chip size="small" variant="tonal" color="primary">
            v{{ plugin.version }}
          </v-chip>

          <v-chip
            size="small"
            variant="tonal"
            :color="stateColor(plugin.state)"
          >
            {{ plugin.state }}
          </v-chip>

          <v-spacer />

          <v-progress-circular
            v-if="togglingId === plugin.id"
            indeterminate
            size="24"
            class="mr-2"
          />

          <v-switch
            :model-value="plugin.enabled"
            color="primary"
            hide-details
            density="compact"
            :disabled="plugin.state === 'FAILED' || togglingId !== null"
            @update:model-value="(val) => onToggle(plugin, val as boolean)"
          />
        </v-card-title>

        <v-card-text class="d-flex flex-column ga-2 pb-3">
          <div v-if="plugin.description" class="text-body-2">
            {{ plugin.description }}
          </div>

          <div class="d-flex align-center flex-wrap ga-2">
            <v-chip
              v-if="plugin.licence"
              size="x-small"
              variant="outlined"
              prepend-icon="mdi-scale-balance"
            >
              {{ plugin.licence }}
            </v-chip>

            <v-btn
              v-if="plugin.homepageUrl"
              :href="plugin.homepageUrl"
              target="_blank"
              rel="noopener noreferrer"
              size="x-small"
              variant="text"
              prepend-icon="mdi-home-outline"
            >
              Homepage
            </v-btn>

            <v-btn
              v-if="plugin.repositoryUrl"
              :href="plugin.repositoryUrl"
              target="_blank"
              rel="noopener noreferrer"
              size="x-small"
              variant="text"
              prepend-icon="mdi-source-repository"
            >
              Repository
            </v-btn>
          </div>

          <v-alert
            v-if="
              (plugin.state === 'FAILED' || plugin.state === 'DEGRADED') &&
              plugin.failureMessage
            "
            type="error"
            variant="tonal"
            density="compact"
            class="mt-1"
          >
            {{ plugin.failureMessage }}
          </v-alert>

          <div
            v-if="plugin.dependencies && plugin.dependencies.length > 0"
            class="d-flex align-center flex-wrap ga-1 mt-1"
          >
            <span class="text-caption text-medium-emphasis">Requires:</span>
            <v-chip
              v-for="dep in plugin.dependencies"
              :key="dep.pluginId"
              size="x-small"
              variant="tonal"
              color="secondary"
            >
              {{ dep.pluginId
              }}<template v-if="dep.versionConstraint">
                &nbsp;{{ dep.versionConstraint }}</template
              >
            </v-chip>
          </div>
        </v-card-text>
      </v-card>
    </template>
  </div>
</template>

<style scoped lang="scss"></style>
