<script setup lang="ts">
// /containers/video/[containerId] — video container scaffold (VID2).
//
// UX-SPATIAL-VIEWER-OR-BANNER: this page is a scaffold showing a clear
// "in development" banner so a user navigating here does not see a blank
// or a 404. The actual video stream viewer (VID2) is not yet implemented.
//
// Video stream references live on DataObjects
//   GET /v2/data-objects/{appId}/video-stream-references
// and are not backed by a separate container type yet. This page is
// pre-wired for when VID2 ships a /v2/video-containers/{id} endpoint.
//
// Three-branch state machine (mirrors the pattern in NotFoundPanel.test.ts):
//   v-if="!!container"       → data loaded, show name + banner
//   v-else-if="isFetchError" → show NotFoundPanel
//   v-else                   → show CenteredLoadingSpinner

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
        <!-- UX-SPATIAL-VIEWER-OR-BANNER: video viewer is in development (VID2).
             This banner replaces a blank page so users know the container exists.
             Remove once the real VID2 viewer ships. -->
        <v-col cols="12" class="mt-4">
          <v-alert
            type="info"
            variant="tonal"
            prepend-icon="mdi-video-outline"
            title="Video stream viewer — in development (VID2)"
            text="The in-browser video viewer is not yet available. Video stream references are accessible from the DataObject detail page. Check back when VID2 ships."
          />
        </v-col>
      </v-row>
      <NotFoundPanel v-else-if="isFetchError" />
      <CenteredLoadingSpinner v-else />
    </v-container>
  </PageShell>
</template>
