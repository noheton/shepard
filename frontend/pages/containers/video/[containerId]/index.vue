<script setup lang="ts">
// /containers/video/[containerId] — video container page (VID1a functional).
//
// VID1a: replaced the "in development" banner (UX-SPATIAL-VIEWER-OR-BANNER)
// with a real VideoStreamReferencesPane. containerId is passed as the
// dataObjectAppId because VideoStreamReferences live on DataObjects and no
// dedicated /v2/video-containers/{id} endpoint exists yet (VID2 deferred).
//
// Three-branch state machine:
//   v-if="!!container"       → data loaded, show video player surface
//   v-else-if="isFetchError" → show NotFoundPanel
//   v-else                   → show CenteredLoadingSpinner

import VideoStreamReferencesPane from "~/components/context/dataobject/VideoStreamReferencesPane.vue";

const route = useRoute();
const containerId = computed(() => String(route.params.containerId ?? ""));

interface VideoContainerShape {
  id: string;
  name: string;
}

const container = ref<VideoContainerShape | null>(null);
const isFetchError = ref(false);

// Synthetic resolve: no /v2/video-containers endpoint exists yet (VID2).
// We surface the containerId as the container name so the page is meaningful.
// When VID2 ships a real endpoint, replace this with the actual fetch call.
async function fetchContainer() {
  isFetchError.value = false;
  container.value = null;
  try {
    // Placeholder: resolve immediately with the containerId as the name.
    // Replace with: const data = await api.getVideoContainer({ containerId: containerId.value })
    container.value = {
      id: containerId.value,
      name: `Video Container ${containerId.value}`,
    };
  } catch {
    isFetchError.value = true;
  }
}

fetchContainer();

// UX Pattern F (2026-05-24): reactive title — call useHead once with a getter.
useHead({
  title: () =>
    container.value?.name
      ? `${container.value.name} (Video) — shepard`
      : "Video Container — shepard",
});
</script>

<template>
  <PageShell>
    <v-container fluid class="pa-0">
      <v-row v-if="!!container" no-gutters>
        <v-col cols="12">
          <Breadcrumbs
            :items="[
              {
                title: 'Containers',
                to: containersPath,
              },
              {
                title: container.name,
                to: '/containers/video/' + containerId,
              },
            ]"
          />
        </v-col>
        <v-col cols="12">
          <v-container class="pa-0" fluid>
            <v-row no-gutters>
              <v-col class="ml-n1 pb-2" cols="12">
                <h1 class="text-h5">{{ container.name }}</h1>
              </v-col>
            </v-row>
          </v-container>
        </v-col>
        <v-col cols="12" class="mt-4">
          <!-- VID1a: video references pane.
               containerId is passed as dataObjectAppId because VideoStreamReferences
               live on DataObjects. When VID2 ships a /v2/video-containers/{id}
               endpoint, resolve linked DataObjects from the container instead. -->
          <VideoStreamReferencesPane
            :data-object-app-id="containerId"
            :can-upload="false"
            :can-edit="false"
          />
        </v-col>
      </v-row>
      <NotFoundPanel v-else-if="isFetchError" />
      <CenteredLoadingSpinner v-else />
    </v-container>
  </PageShell>
</template>
