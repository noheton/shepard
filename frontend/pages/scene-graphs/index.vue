<script setup lang="ts">
/**
 * /scene-graphs — index / landing page for the scene-graph viewer.
 *
 * The 3D viewer lives at /scene-graphs/play/{templateAppId}. A template
 * appId is required to render anything — it identifies the MAPPING_RECIPE
 * template that drives vis-trace3d. This landing page provides two entry
 * paths:
 *
 * 1. In-context (primary) — navigate to a URDF or RDK FileReference and
 *    click "Open in 3D view" / "Create 3D view". The button auto-detects
 *    scene-graph-eligible files and routes directly to the play page with
 *    the correct template appId.
 *
 * 2. Direct launch — paste a template appId into the form below and press
 *    Enter. Useful for bookmarked scenes or URLs shared via Slack.
 *
 * Companion row: UI-1920-SCENEGRAPH-NAV in aidocs/16-dispatcher-backlog.md.
 * Cross-ref: SCENEGRAPH-NAV-01 (listed index); the "real" list endpoint
 * is GET /v2/scene-graphs (future work when SCENEGRAPH-LIST-1 is built).
 */

useHead({ title: "Scene-graph viewer | shepard" });

const router = useRouter();
const templateAppId = ref("");

function isPlausibleAppId(s: string): boolean {
  // UUID v7 is 8-4-4-4-12 hex with leading time component
  return /^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(s.trim());
}

const appIdError = computed(() => {
  const v = templateAppId.value.trim();
  if (!v) return "";
  return isPlausibleAppId(v) ? "" : "Enter a valid UUID v7 (e.g. 0192abcd-…-7…)";
});

function open() {
  const id = templateAppId.value.trim();
  if (!isPlausibleAppId(id)) return;
  router.push(`/scene-graphs/play/${id}`);
}
</script>

<template>
  <v-container class="pa-6" fluid style="max-width: 960px; margin: 0 auto">
    <div class="d-flex align-center ga-2 mb-4">
      <v-icon icon="mdi-cube-scan" color="primary" size="36" />
      <h1 class="text-h4">Scene-graph viewer</h1>
    </div>

    <p class="text-body-1 text-medium-emphasis mb-8">
      The 3D viewer renders robot URDF models — frames, joints, and live
      timeseries channel bindings — interactively in the browser.
    </p>

    <!-- In-context entry (primary) -->
    <v-card variant="tonal" color="primary" class="mb-6">
      <v-card-item>
        <template #prepend>
          <v-icon icon="mdi-file-cog-outline" size="28" />
        </template>
        <v-card-title>Open from a URDF or RDK file</v-card-title>
      </v-card-item>
      <v-card-text>
        Navigate to the DataObject that holds your robot file, open the
        <strong>References</strong> panel, and click the URDF or RDK
        FileReference row. Then click
        <strong>Open in 3D view</strong> (or <strong>Create 3D view</strong>
        if no scene has been minted yet). This is the recommended entry point.
      </v-card-text>
    </v-card>

    <!-- Direct launch -->
    <v-card variant="outlined" class="mb-6">
      <v-card-item>
        <template #prepend>
          <v-icon icon="mdi-identifier" size="28" />
        </template>
        <v-card-title>Open a scene by template appId</v-card-title>
      </v-card-item>
      <v-card-text>
        <p class="text-body-2 text-medium-emphasis mb-4">
          If you have a template appId (UUID v7) from a shared link or a
          previous session, paste it here.
        </p>
        <v-form @submit.prevent="open">
          <v-text-field
            v-model="templateAppId"
            label="Template appId"
            placeholder="0192abcd-1234-7abc-89ef-000000000001"
            variant="outlined"
            density="compact"
            :error-messages="appIdError"
            clearable
            data-testid="template-appid-input"
            @keyup.enter="open"
          />
          <v-btn
            color="primary"
            variant="flat"
            :disabled="!isPlausibleAppId(templateAppId.trim())"
            type="submit"
            data-testid="open-btn"
            class="mt-2"
          >
            Open 3D view
          </v-btn>
        </v-form>
      </v-card-text>
    </v-card>

    <!-- Materialize-mapping fallback -->
    <v-card variant="outlined" class="mb-6">
      <v-card-item>
        <template #prepend>
          <v-icon icon="mdi-map-marker-path" size="28" />
        </template>
        <v-card-title>Browse templates in the mapping tool</v-card-title>
      </v-card-item>
      <v-card-text class="pb-2">
        The <strong>Mapping tool</strong> lists all <code>MAPPING_RECIPE</code>
        templates you have access to, lets you select reference bindings, and
        opens the 3D view when the executor is <code>vis-trace3d</code>.
      </v-card-text>
      <v-card-actions>
        <v-btn
          to="/tools/materialize-mapping"
          color="secondary"
          variant="tonal"
          prepend-icon="mdi-map-marker-path"
          data-testid="mapping-tool-btn"
        >
          Open mapping tool
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-container>
</template>
