<script setup lang="ts">
/**
 * SCENE-GRAPHS-INDEX-2026-06-29 — top-level `/scene-graphs` listing.
 *
 * V2CONV-B4 dissolved the bespoke stored `/v2/scene-graphs/*` namespace
 * into the generic MAPPING_RECIPE mechanism — every scene-graph play
 * recipe is now a `ShepardTemplate{ templateKind: "MAPPING_RECIPE",
 * mappingRecipeShape: SCENE_GRAPH_PLAY_SHAPE_IRI }`. The play page lives
 * at `/scene-graphs/play/{templateAppId}` (already shipped); this page
 * surfaces the index a user reaches by clicking "Scene graphs" from
 * the Tools menu or by typing /scene-graphs.
 *
 * Backend list endpoint: `GET /v2/templates?templateKind=MAPPING_RECIPE`.
 * We filter client-side on `mappingRecipeShape === SCENE_GRAPH_PLAY_SHAPE_IRI`
 * (the field lives inside the template's stringified `body`).
 *
 * Empty state points the user to the in-context creation flow on a URDF
 * FileReference detail page ("Open in 3D view") per the
 * "Tools entry points are in-context first" CLAUDE.md rule.
 */
import { TemplatesApi, type ShepardTemplate } from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";
import { SCENE_GRAPH_PLAY_SHAPE_IRI } from "~/composables/useSceneGraphPlay";

useHead({ title: "Scene graphs | shepard" });

interface SceneGraphRow {
  appId: string;
  name: string;
  description?: string | null;
  createdAt?: string | null;
  createdBy?: string | null;
  urdfFileReferenceAppId?: string | null;
}

const rows = ref<SceneGraphRow[]>([]);
const isLoading = ref(true);
const loadError = ref<string | null>(null);

const templatesApi = useV2ShepardApi(TemplatesApi);

function parseRecipeBody(body: string | null | undefined): Record<string, unknown> | null {
  if (!body) return null;
  try {
    const parsed = JSON.parse(body) as Record<string, unknown>;
    return parsed;
  } catch {
    return null;
  }
}

function toRow(t: ShepardTemplate): SceneGraphRow | null {
  const parsed = parseRecipeBody((t as unknown as { body?: string | null }).body);
  if (!parsed) return null;
  if (parsed.mappingRecipeShape !== SCENE_GRAPH_PLAY_SHAPE_IRI) return null;
  return {
    appId: t.appId,
    name: t.name,
    description: (t as unknown as { description?: string | null }).description ?? null,
    createdAt: (t as unknown as { createdAt?: string | null }).createdAt ?? null,
    createdBy: (t as unknown as { createdBy?: string | null }).createdBy ?? null,
    urdfFileReferenceAppId:
      (parsed.urdfFileReferenceAppId as string | undefined) ?? null,
  };
}

async function refresh() {
  isLoading.value = true;
  loadError.value = null;
  try {
    const page = await templatesApi.value.listTemplates({
      kind: "MAPPING_RECIPE",
      includeRetired: false,
      pageSize: 200,
    });
    const items = (page.items ?? []) as ShepardTemplate[];
    rows.value = items
      .map(toRow)
      .filter((r): r is SceneGraphRow => r !== null);
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : "Failed to load templates.";
    handleError(e, "list scene-graph play recipes");
    rows.value = [];
  } finally {
    isLoading.value = false;
  }
}

refresh();
</script>

<template>
  <v-container class="pa-6" fluid style="max-width: 2400px; margin: 0 auto">
    <h1 class="text-h4 mb-2">Scene graphs</h1>
    <p class="text-body-1 text-medium-emphasis mb-6">
      Scene-graph play recipes — MAPPING_RECIPE templates targeting
      <code>SceneGraphPlayShape</code> that bind a URDF FileReference (the
      kinematic tree) and optionally a joint TimeseriesReference for playback.
    </p>

    <v-alert
      v-if="loadError"
      type="error"
      variant="tonal"
      class="mb-4"
      data-testid="scene-graphs-error"
    >
      {{ loadError }}
    </v-alert>

    <v-card variant="outlined">
      <v-data-table
        v-if="!isLoading && rows.length > 0"
        :items="rows"
        :headers="[
          { title: 'Name', key: 'name' },
          { title: 'URDF FileReference', key: 'urdfFileReferenceAppId' },
          { title: 'Created', key: 'createdAt' },
          { title: 'By', key: 'createdBy' },
          { title: '', key: 'actions', sortable: false, align: 'end' },
        ]"
        :items-per-page="50"
        density="comfortable"
        data-testid="scene-graphs-table"
      >
        <template #[`item.urdfFileReferenceAppId`]="{ item }">
          <code v-if="item.urdfFileReferenceAppId" class="text-caption">{{
            item.urdfFileReferenceAppId
          }}</code>
          <span v-else class="text-medium-emphasis">—</span>
        </template>
        <template #[`item.createdAt`]="{ item }">
          <span v-if="item.createdAt" class="text-caption">{{
            new Date(item.createdAt).toISOString().slice(0, 10)
          }}</span>
          <span v-else class="text-medium-emphasis">—</span>
        </template>
        <template #[`item.actions`]="{ item }">
          <v-btn
            :to="`/scene-graphs/play/${item.appId}`"
            variant="tonal"
            size="small"
            prepend-icon="mdi-play"
            :data-testid="`scene-graph-play-${item.appId}`"
          >
            Open
          </v-btn>
        </template>
      </v-data-table>

      <div
        v-else-if="isLoading"
        class="d-flex align-center justify-center pa-12"
        data-testid="scene-graphs-loading"
      >
        <v-progress-circular indeterminate color="primary" />
      </div>

      <v-empty-state
        v-else
        icon="mdi-cube-scan"
        title="No scene-graph play recipes yet"
        text="Open a URDF FileReference and use 'Open in 3D view' to create one."
        data-testid="scene-graphs-empty"
      />
    </v-card>
  </v-container>
</template>
