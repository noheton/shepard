<script setup lang="ts">
import { VersionzApi } from "@dlr-shepard/backend-client";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import { useInstanceCapabilities } from "~/composables/context/useInstanceCapabilities";
import { AboutFragments } from "./aboutMenuItems";

const backendURL = useRuntimeConfig().public.backendApiUrl;
const applicationVersion = ref<string>();

async function fetchApplicationVersion() {
  useShepardApi(VersionzApi)
    .value.getShepardVersion()
    .then(result => {
      applicationVersion.value = result.version;
    })
    .catch(e => {
      handleError(e, "fetching backend version");
    });
}

fetchApplicationVersion();

const { capabilities, loaded: capabilitiesLoaded } = useInstanceCapabilities();
</script>

<template>
  <div :id="AboutFragments.VERSION" class="d-flex flex-column ga-4">
    <div class="text-h4 pb-2">Version</div>

    <div class="d-flex flex-column ga-2">
      <div>
        <strong>Application version:</strong>
        &nbsp;
        <code>{{ applicationVersion ?? "—" }}</code>
      </div>
      <div>
        <strong>Backend URL:</strong>
        &nbsp;
        <code>{{ backendURL }}</code>
      </div>
    </div>

    <div>
      <div class="text-subtitle-1 font-weight-medium mb-2">Active plugins</div>
      <div v-if="!capabilitiesLoaded" class="d-flex align-center ga-2 text-medium-emphasis text-body-2">
        <v-progress-circular indeterminate size="14" width="2" />
        Loading…
      </div>
      <div
        v-else-if="!capabilities || capabilities.plugins.length === 0"
        class="text-body-2 text-medium-emphasis"
      >
        No plugins enabled on this instance.
      </div>
      <div v-else class="d-flex flex-wrap ga-2">
        <v-chip
          v-for="plugin in capabilities.plugins"
          :key="plugin.id"
          size="small"
          variant="tonal"
          color="primary"
          prepend-icon="mdi-puzzle-outline"
        >
          {{ plugin.title }}
          <template #append>
            <span class="text-caption text-medium-emphasis ml-1">{{ plugin.version }}</span>
          </template>
        </v-chip>
      </div>
    </div>
  </div>
</template>
