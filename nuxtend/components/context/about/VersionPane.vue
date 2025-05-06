<script setup lang="ts">
import { VersionzApi } from "@dlr-shepard/backend-client";
import { AboutFragments } from "./aboutMenuItems";

const backendURL = useRuntimeConfig().public.backendApiUrl;
const applicationVersion = ref<string>();

async function fetchApplicationVersion() {
  createApiInstance(VersionzApi)
    .getShepardVersion()
    .then(result => {
      applicationVersion.value = result.version;
    })
    .catch(e => {
      handleError(e, "fetching backend version");
    });
}

fetchApplicationVersion();
</script>

<template>
  <div :id="AboutFragments.VERSION" class="d-flex flex-column ga-2">
    <div class="text-h4 pb-4">Version</div>
    <div>
      <strong>Application Version:</strong>
      &nbsp;
      <code>{{ applicationVersion }}</code>
    </div>
    <div>
      <strong>Backend URL:</strong>
      &nbsp;
      <code>{{ backendURL }}</code>
    </div>
  </div>
</template>
