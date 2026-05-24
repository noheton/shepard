<script setup lang="ts">
// /containers/hdf/[containerId] — placeholder HDF container browser.
// Backend A5 (a/b/d) shipped; navigating HDF5 datasets in-browser is not
// yet built. The placeholder:
//   - shows the container appId and metadata
//   - links to the byte-identical download (/v2/hdf-containers/{}/file)
//   - in advanced mode, dumps the raw container metadata REST response

import PlaceholderPageHeader from "~/components/common/placeholder/PlaceholderPageHeader.vue";
import PlaceholderRestDump from "~/components/common/placeholder/PlaceholderRestDump.vue";
import PlaceholderImplStatus from "~/components/common/placeholder/PlaceholderImplStatus.vue";

const route = useRoute();
const containerId = computed(() => String(route.params.containerId ?? ""));

useHead({ title: () => `${containerId.value} | HDF | shepard` });

const containerEndpoint = computed(
  () => `/v2/hdf-containers/${encodeURIComponent(containerId.value)}`,
);
const downloadEndpoint = computed(
  () => `/v2/hdf-containers/${encodeURIComponent(containerId.value)}/file`,
);
</script>

<template>
  <v-container>
    <PlaceholderPageHeader
      title="HDF container"
      :subtitle="`HDF5 container ${containerId} — full in-browser dataset navigation is queued under A5-UI-PHASE-1. Today the container is reachable via the HSDS sidecar API and a byte-identical original-file download.`"
      design-doc-href="https://github.com/noheton/shepard/blob/main/aidocs/data/35-hdf5-hsds-implementation-design.md"
      design-doc-label="A5 design (aidocs/data/35)"
    />
    <v-card variant="outlined" class="mb-3">
      <v-card-title class="text-subtitle-1">Direct actions</v-card-title>
      <v-card-text class="d-flex flex-column ga-2">
        <v-btn
          variant="tonal"
          prepend-icon="mdi-download"
          :href="downloadEndpoint"
        >
          Download original HDF5 file
        </v-btn>
        <p class="text-caption text-medium-emphasis">
          Range requests pass through; <code>h5pyd</code> users should hit
          the HSDS sidecar directly (configured per-instance; see the A5
          install guide).
        </p>
      </v-card-text>
    </v-card>
    <PlaceholderImplStatus
      backend="partial"
      backlog-row="A5"
      design-doc="aidocs/data/35-hdf5-hsds-implementation-design.md"
      :endpoint="containerEndpoint"
      notes="Backend phases A5a-A5d shipped; UI navigation queued (A5-UI-PHASE-1)."
    />
    <PlaceholderRestDump
      :endpoint="containerEndpoint"
      hint="Raw HDF container metadata (advanced mode)."
    />
  </v-container>
</template>
