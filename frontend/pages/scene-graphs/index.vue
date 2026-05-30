<script setup lang="ts">
/**
 * Scene graphs landing — SCENEGRAPH-NAV-01 → SCENEGRAPH-LIST-1.
 *
 * Sibling to `/scene-graphs/[appId]` (SCENEGRAPH-REST-1-UI). A 1-click
 * destination from the top-level Tools menu (TOOLS-NAV-01).
 *
 * Wire shape: `GET /v2/scene-graphs?page=<n>&size=<m>` (C1, shipped
 * 2026-05-30 as `db241cff2`) returns `{ items[], total, page, size }`.
 * We render via `v-data-table-server` for proper server-side pagination
 * and degrade to the existing help card when the catalogue is empty.
 *
 * The open-by-appId form lives below the table as a power-user shortcut —
 * still respecting the "UI never asks for paths/URLs" rule (appId is a
 * stable identity handle, not a path or URL).
 */

import { isPlausibleAppId } from "~/utils/toolsLanding";
import {
  formatEpochMillis,
  resolveLandingBranch,
  truncateAppId,
} from "~/utils/sceneGraphsLanding";
import { useSceneGraph, type SceneListItem } from "~/composables/useSceneGraph";

const router = useRouter();
const { fetchScene, list, error: sceneError } = useSceneGraph();

// ── List state (SCENEGRAPH-LIST-1) ──────────────────────────────────────────

const rows = ref<SceneListItem[]>([]);
const totalRows = ref(0);
const page = ref(1); // v-data-table-server is 1-based; backend is 0-based.
const itemsPerPage = ref(25);
const listLoading = ref(false);
const listError = ref<string | null>(null);

const headers = [
  { title: "Name", key: "name", sortable: false },
  { title: "# frames", key: "frameCount", sortable: false, align: "end" as const },
  { title: "# joints", key: "jointCount", sortable: false, align: "end" as const },
  { title: "Updated", key: "updatedAt", sortable: false },
  { title: "AppId", key: "appId", sortable: false },
];

async function loadPage(opts: { page: number; itemsPerPage: number }): Promise<void> {
  listLoading.value = true;
  listError.value = null;
  const result = await list({
    page: Math.max(0, opts.page - 1),
    size: opts.itemsPerPage,
  });
  if (result) {
    rows.value = result.items ?? [];
    totalRows.value = result.total ?? 0;
  } else {
    rows.value = [];
    totalRows.value = 0;
    listError.value =
      sceneError.value?.message ?? "Could not load scene graphs.";
  }
  listLoading.value = false;
}

const landingBranch = computed(() =>
  resolveLandingBranch(totalRows.value, listLoading.value, listError.value),
);

async function copyAppId(appId: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(appId);
  } catch {
    // Clipboard may be unavailable (e.g. http test contexts); silently no-op.
  }
}

// ── Open-by-appId fallback (power user shortcut) ───────────────────────────

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
      v-if="landingBranch === 'error'"
      type="error"
      variant="tonal"
      class="mb-4"
      data-testid="scene-graphs-list-error"
    >
      {{ listError }}
    </v-alert>

    <v-card
      v-if="landingBranch === 'table'"
      variant="outlined"
      class="mb-6"
      data-testid="scene-graphs-list-card"
    >
      <v-data-table-server
        v-model:items-per-page="itemsPerPage"
        v-model:page="page"
        :headers="headers"
        :items="rows"
        :items-length="totalRows"
        :loading="listLoading"
        :items-per-page-options="[10, 25, 50, 100]"
        item-value="appId"
        density="comfortable"
        data-testid="scene-graphs-list-table"
        @update:options="loadPage"
      >
        <template #[`item.name`]="{ item }">
          <a
            class="scene-row-link"
            :href="`/scene-graphs/${item.appId}`"
            data-testid="scene-graphs-row-name"
            @click.prevent="router.push(`/scene-graphs/${item.appId}`)"
          >
            {{ item.name?.trim() || "(unnamed scene)" }}
          </a>
        </template>
        <template #[`item.updatedAt`]="{ item }">
          {{ formatEpochMillis(item.updatedAt ?? item.createdAt) }}
        </template>
        <template #[`item.appId`]="{ item }">
          <span
            class="appid-mono"
            :title="item.appId"
            data-testid="scene-graphs-row-appid"
            @click.stop="copyAppId(item.appId)"
          >
            {{ truncateAppId(item.appId) }}
          </span>
        </template>
      </v-data-table-server>
    </v-card>

    <v-card
      v-if="landingBranch === 'help'"
      variant="outlined"
      class="mb-6"
      data-testid="scene-graphs-empty-card"
    >
      <v-card-title>No scenes yet</v-card-title>
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
            new <code>appId</code> you can paste below.
          </li>
        </ul>
      </v-card-text>
    </v-card>

    <v-expansion-panels variant="accordion" class="mb-2">
      <v-expansion-panel data-testid="scene-graphs-open-by-appid-panel">
        <v-expansion-panel-title>Open by appId</v-expansion-panel-title>
        <v-expansion-panel-text>
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
        </v-expansion-panel-text>
      </v-expansion-panel>
    </v-expansion-panels>
  </v-container>
</template>

<style scoped>
.scene-row-link {
  color: inherit;
  text-decoration: none;
  font-weight: 500;
}

.scene-row-link:hover {
  text-decoration: underline;
}

.appid-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.85em;
  cursor: copy;
}
</style>
