<script setup lang="ts">
/**
 * Scene graphs landing — SCENEGRAPH-NAV-01.
 *
 * Sibling to `/scene-graphs/[appId]` (SCENEGRAPH-REST-1-UI). A 1-click
 * destination from the new top-level Tools menu (TOOLS-NAV-01).
 *
 * Why no list table yet: the backend exposes `GET /v2/scene-graphs/{appId}`
 * + `POST /v2/scene-graphs` but no `GET /v2/scene-graphs` list endpoint.
 * Filed as SCENEGRAPH-LIST-1 in aidocs/16. Until that ships, this page
 * lets the user:
 *   - Mint a new empty scene (POST /v2/scene-graphs, then route to it).
 *   - Open an existing scene by appId (the stable identity handle).
 *   - Read a one-liner pointing at the URDF / RDK upload entry points.
 *
 * Following the "UI never asks for paths/URLs" rule — appId is a stable
 * identity handle, not a path or URL, so the open-by-id input is OK.
 */

import { isPlausibleAppId } from "~/utils/toolsLanding";

const router = useRouter();
const { fetchScene } = useSceneGraph();

const openByAppId = ref("");
const openError = ref<string | null>(null);
const opening = ref(false);

async function openScene() {
  const id = openByAppId.value.trim();
  if (!id) {
    openError.value = "Enter a scene appId.";
    return;
  }
  if (!isPlausibleAppId(id)) {
    openError.value = "That doesn't look like a UUID — expected 8-4-4-4-12 hex.";
    return;
  }
  openError.value = null;
  opening.value = true;
  try {
    const scene = await fetchScene(id);
    if (scene) {
      await router.push(`/scene-graphs/${id}`);
    } else {
      openError.value = "No scene found with that appId, or you lack access.";
    }
  } finally {
    opening.value = false;
  }
}

useHead({ title: "Scene graphs | shepard" });
</script>

<template>
  <v-container class="pa-6" style="max-width: 1100px">
    <h1 class="text-h4 mb-2">Scene graphs</h1>
    <p class="text-body-1 text-medium-emphasis mb-6">
      Coordinate-frame trees + joints describing a digital twin or robot model.
      Scenes are created from URDF / RDK uploads, or directly via
      <code>POST /v2/scene-graphs</code> from the API.
    </p>

    <v-alert
      type="info"
      variant="tonal"
      class="mb-6"
      data-testid="scene-graphs-no-list-notice"
    >
      A list view across all your scenes is queued as
      <strong>SCENEGRAPH-LIST-1</strong> (the backend list endpoint is not
      yet exposed). Until then, open a scene below by its appId, or land on
      one from a URDF FileReference's "Open in scene viewer" action.
    </v-alert>

    <v-card variant="outlined" class="mb-6">
      <v-card-title>Open by appId</v-card-title>
      <v-card-text>
        <v-form @submit.prevent="openScene">
          <v-text-field
            v-model="openByAppId"
            label="Scene appId (UUID v7)"
            placeholder="0197b6a2-…"
            density="compact"
            variant="outlined"
            hide-details="auto"
            :error-messages="openError ? [openError] : []"
            :loading="opening"
            data-testid="scene-graphs-open-by-appid-input"
            autocomplete="off"
          />
          <div class="mt-3">
            <v-btn
              color="primary"
              type="submit"
              :disabled="opening"
              data-testid="scene-graphs-open-by-appid-submit"
            >
              Open scene
            </v-btn>
          </div>
        </v-form>
      </v-card-text>
    </v-card>

    <v-card variant="outlined" data-testid="scene-graphs-help-card">
      <v-card-title>How to get a scene</v-card-title>
      <v-card-text>
        <ul class="ml-4">
          <li>
            Upload a URDF file as a FileReference, then use its "Open in
            scene viewer" action.
          </li>
          <li>
            Drop an RDK package into a Collection; the RDK importer mints a
            scene per robot.
          </li>
          <li>
            From the API:
            <code>POST /v2/scene-graphs</code> with an empty body returns a
            new <code>appId</code> you can paste above.
          </li>
        </ul>
      </v-card-text>
    </v-card>
  </v-container>
</template>
