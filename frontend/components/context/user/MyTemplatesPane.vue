<script setup lang="ts">
/**
 * TPL-ME-BROWSE-1 — non-admin browse-mine surface for ShepardTemplate.
 *
 * Backend (`GET /v2/templates`, `ShepardTemplateRest`) is readable by any
 * authenticated user; only POST / PATCH / DELETE are gated to
 * `instance-admin`. Pre-this pane, researchers could only see what
 * templates exist via the picker dropdown in `CreateDataObjectDialog` —
 * which requires already being mid-create-flow inside a Collection.
 * The Reluctant Senior Researcher persona wanted a flat "what's on this
 * instance" answer; this pane is that.
 *
 * The "Use" affordance is read-only here: instantiation needs a
 * Collection context (`POST /v2/collections/{collectionAppId}/templates/
 * {templateAppId}/data-object`), so we open a details dialog with the
 * body preview and a hint to create the DataObject from inside a
 * Collection. Follow-up: TPL-ME-USE-FROM-BROWSE (a Collection picker).
 */
import {
  TemplatesApi,
  type ShepardTemplate,
} from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";

const templates = ref<ShepardTemplate[]>([]);
const isLoading = ref(false);
const loadError = ref<string | null>(null);
const filter = ref("");
const selected = ref<ShepardTemplate | null>(null);
const showDetails = ref(false);

function load() {
  isLoading.value = true;
  loadError.value = null;
  useV2ShepardApi(TemplatesApi)
    .value.listTemplates({})
    .then(rows => {
      templates.value = rows ?? [];
    })
    .catch(err => {
      // 403 here means the deployment routed the GET behind admin-only.
      // Treat as empty + tell the user.
      loadError.value =
        (err as { response?: { status?: number } })?.response?.status === 403
          ? "Templates are not viewable on this instance."
          : "Failed to load templates.";
      templates.value = [];
    })
    .finally(() => {
      isLoading.value = false;
    });
}

onMounted(load);

const filtered = computed(() => {
  const q = filter.value.trim().toLowerCase();
  if (!q) return templates.value;
  return templates.value.filter(
    t =>
      (t.name ?? "").toLowerCase().includes(q) ||
      (t.description ?? "").toLowerCase().includes(q) ||
      (t.templateKind ?? "").toLowerCase().includes(q) ||
      (t.tags ?? []).some(tag => tag.toLowerCase().includes(q)),
  );
});

function shippedVia(t: ShepardTemplate): string {
  // Lightweight heuristic — system seeds are created by the migrations
  // runner under a service principal; git imports leave a `git:` tag per
  // TPL5; everything else is admin upload. Adjust as more sources land.
  const tags = t.tags ?? [];
  if (tags.some(tag => tag.startsWith("system:") || tag === "system")) return "system";
  if (tags.some(tag => tag.startsWith("git:") || tag === "git")) return "git import";
  return "admin upload";
}

function openDetails(t: ShepardTemplate) {
  selected.value = t;
  showDetails.value = true;
}
</script>

<template>
  <div class="pa-4">
    <h2 class="text-h5 mb-2">Templates on this instance</h2>
    <p class="text-body-2 text-medium-emphasis mb-4">
      Read-only catalogue of every <code>ShepardTemplate</code> available
      on this Shepard. Admins seed templates via the Admin Templates pane
      or the importer; you can browse them here before opening
      <strong>Create DataObject</strong> inside a Collection — which is
      where instantiation happens (templates carry the recipe; the
      Collection carries the destination).
    </p>

    <v-text-field
      v-model="filter"
      label="Filter by name, kind, description, tag"
      prepend-inner-icon="mdi-magnify"
      variant="outlined"
      density="compact"
      hide-details
      clearable
      class="mb-4"
      data-test="template-filter"
    />

    <v-progress-linear v-if="isLoading" indeterminate class="mb-2" />

    <v-alert
      v-if="loadError"
      type="warning"
      variant="tonal"
      density="compact"
      class="mb-4"
    >
      {{ loadError }}
    </v-alert>

    <v-alert
      v-else-if="!isLoading && templates.length === 0"
      type="info"
      variant="tonal"
      density="compact"
      class="mb-4"
      data-test="templates-empty"
    >
      No templates available on this instance. Admins can seed templates
      via the
      <NuxtLink to="/admin#templates" class="text-primary">
        Admin Templates pane
      </NuxtLink>
      or the git-importer.
    </v-alert>

    <v-card v-else-if="!isLoading" variant="outlined" data-test="templates-card">
      <v-data-table
        :headers="[
          { title: '', key: 'icon', sortable: false, width: 40 },
          { title: 'Name', key: 'name', sortable: true },
          { title: 'Kind', key: 'templateKind', sortable: true },
          { title: 'Description', key: 'description', sortable: false },
          { title: 'Shipped via', key: 'shippedVia', sortable: true },
          { title: '', key: 'actions', sortable: false, align: 'end' },
        ]"
        :items="filtered.map(t => ({ ...t, shippedVia: shippedVia(t) }))"
        :items-per-page="25"
        density="compact"
        data-test="templates-table"
      >
        <template #[`item.icon`]="{ item }">
          <!-- TEMPLATE-ICONS-2-FE — template's icon (or per-kind default) -->
          <v-icon
            :icon="useTemplateIcon(item, 'DataObject')"
            size="small"
            data-test="template-icon"
          />
        </template>
        <template #[`item.description`]="{ item }">
          <span class="text-medium-emphasis">
            {{ (item.description ?? "").slice(0, 100) }}
            {{ (item.description ?? "").length > 100 ? "…" : "" }}
          </span>
        </template>
        <template #[`item.actions`]="{ item }">
          <v-btn
            size="small"
            variant="text"
            prepend-icon="mdi-eye-outline"
            data-test="template-details-btn"
            @click="openDetails(item)"
          >
            Details
          </v-btn>
        </template>
      </v-data-table>
    </v-card>

    <!-- Details dialog — read-only body preview + hint -->
    <v-dialog v-model="showDetails" max-width="720">
      <v-card v-if="selected">
        <v-card-title>
          <v-icon
            :icon="useTemplateIcon(selected, 'DataObject')"
            class="mr-2"
            size="small"
          />
          {{ selected.name }}
          <v-chip
            size="x-small"
            class="ml-2"
            color="primary"
            variant="tonal"
          >
            {{ selected.templateKind }}
          </v-chip>
          <v-chip size="x-small" class="ml-1" variant="outlined">
            v{{ selected.version }}
          </v-chip>
        </v-card-title>
        <v-card-text>
          <p v-if="selected.description" class="text-body-2 mb-3">
            {{ selected.description }}
          </p>
          <div v-if="(selected.tags ?? []).length > 0" class="mb-3">
            <v-chip
              v-for="tag in selected.tags ?? []"
              :key="tag"
              size="x-small"
              class="mr-1"
              variant="tonal"
            >
              {{ tag }}
            </v-chip>
          </div>
          <v-alert type="info" variant="tonal" density="compact" class="mb-3">
            To use this template, open a Collection and click
            <strong>Create DataObject</strong>. The picker will offer
            this template (when the Collection allows it).
          </v-alert>
          <details>
            <summary class="text-caption text-medium-emphasis cursor-pointer">
              Recipe body (JSON)
            </summary>
            <pre class="text-caption pa-2 mt-2 rounded bg-grey-lighten-4" style="overflow:auto;max-height:300px;">{{ selected.body }}</pre>
          </details>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="showDetails = false">Close</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>
