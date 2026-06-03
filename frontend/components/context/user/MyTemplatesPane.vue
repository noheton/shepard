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
 * TPL-ME-USE-FROM-BROWSE — "Use in Collection…" affordance added.
 * Opens a Collection picker inside the details dialog; on confirm calls
 * CollectionTemplateApi.instantiateDataObject and navigates to the new
 * DataObject.
 */
import {
  CollectionTemplateApi,
  ShepardTemplateApi,
  type ShepardTemplateIO,
} from "@dlr-shepard/backend-client";
import { useTimeoutFn } from "@vueuse/core";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";
import {
  useCollectionSearch,
  type MyCollectionSearchResult,
} from "~/composables/context/useCollectionSearch";
import { collectionsPath, dataObjectsPathFragment } from "~/utils/constants";

const router = useRouter();

const templates = ref<ShepardTemplateIO[]>([]);
const isLoading = ref(false);
const loadError = ref<string | null>(null);
const filter = ref("");
const selected = ref<ShepardTemplateIO | null>(null);
const showDetails = ref(false);

function load() {
  isLoading.value = true;
  loadError.value = null;
  useV2ShepardApi(ShepardTemplateApi)
    .value.getTemplates({})
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

function shippedVia(t: ShepardTemplateIO): string {
  // Lightweight heuristic — system seeds are created by the migrations
  // runner under a service principal; git imports leave a `git:` tag per
  // TPL5; everything else is admin upload. Adjust as more sources land.
  const tags = t.tags ?? [];
  if (tags.some(tag => tag.startsWith("system:") || tag === "system")) return "system";
  if (tags.some(tag => tag.startsWith("git:") || tag === "git")) return "git import";
  return "admin upload";
}

function openDetails(t: ShepardTemplateIO) {
  selected.value = t;
  showDetails.value = true;
}

// ── "Use in Collection…" state (TPL-ME-USE-FROM-BROWSE) ─────────────────────

interface AutoCompleteItem {
  title: string;
  value: MyCollectionSearchResult;
}

const showCollectionPicker = ref(false);
const collectionSearchString = ref("");
const hideNoDataMessage = ref(true);
const selectedCollection = ref<AutoCompleteItem | undefined>(undefined);
const isInstantiating = ref(false);
const instantiateError = ref<string | null>(null);

const {
  collectionSearchResults,
  startSearch,
  isLoading: isSearching,
  resetResultList,
} = useCollectionSearch(collectionSearchString, () => {
  hideNoDataMessage.value = false;
});

const { isPending, start: startDebounce } = useTimeoutFn(() => {
  if (collectionSearchString.value.trim() === "") {
    hideNoDataMessage.value = true;
  }
  startSearch();
}, 350);

function mapToItem(r: MyCollectionSearchResult): AutoCompleteItem {
  return {
    title: `${r.collectionName} (ID: ${r.collectionId})`,
    value: r,
  };
}

function onCollectionSearch(search: string) {
  collectionSearchString.value = search;
  if (!isPending.value) {
    startDebounce();
  }
}

function openCollectionPicker() {
  showCollectionPicker.value = true;
  selectedCollection.value = undefined;
  collectionSearchString.value = "";
  hideNoDataMessage.value = true;
  instantiateError.value = null;
  resetResultList();
}

async function confirmInstantiate() {
  if (!selected.value || !selectedCollection.value) return;
  const collectionResult = selectedCollection.value.value;
  // The backend CollectionTemplateApi.instantiateDataObject needs collectionAppId.
  // The search returns numeric id + name; we use String(collectionId) as the
  // backend resolves both numeric legacy id and UUID v7 appId via its dual-resolver.
  const collectionAppId = String(collectionResult.collectionId);
  const templateAppId = selected.value.appId;

  isInstantiating.value = true;
  instantiateError.value = null;
  try {
    const created = await useV2ShepardApi(CollectionTemplateApi)
      .value.instantiateDataObject({
        collectionAppId,
        templateAppId,
      });
    emitSuccess(
      `Created "${created.name}" from template "${selected.value.name}"`,
    );
    showCollectionPicker.value = false;
    showDetails.value = false;
    router.push(
      collectionsPath +
        collectionResult.collectionId +
        dataObjectsPathFragment +
        created.id,
    );
  } catch (err) {
    instantiateError.value =
      (err as { message?: string })?.message ??
      "Failed to create DataObject from template.";
  } finally {
    isInstantiating.value = false;
  }
}
</script>

<template>
  <div class="pa-4">
    <h2 class="text-h5 mb-2">Templates on this instance</h2>
    <p class="text-body-2 text-medium-emphasis mb-4">
      Browse every <code>ShepardTemplate</code> available on this Shepard.
      Open <strong>Details</strong> to preview the recipe and use
      <strong>Use in Collection&hellip;</strong> to create a DataObject from
      it directly. Admins seed templates via the Admin Templates pane or the
      importer.
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

    <!-- Details dialog — body preview + "Use in Collection…" affordance -->
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
          <details>
            <summary class="text-caption text-medium-emphasis cursor-pointer">
              Recipe body (JSON)
            </summary>
            <pre class="text-caption pa-2 mt-2 rounded bg-grey-lighten-4" style="overflow:auto;max-height:300px;">{{ selected.body }}</pre>
          </details>
        </v-card-text>
        <v-card-actions>
          <v-btn
            color="primary"
            variant="tonal"
            prepend-icon="mdi-folder-open-outline"
            data-test="use-in-collection-btn"
            @click="openCollectionPicker"
          >
            Use in Collection&hellip;
          </v-btn>
          <v-spacer />
          <v-btn variant="text" @click="showDetails = false">Close</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- Collection picker dialog — shown on top of details dialog -->
    <v-dialog
      v-model="showCollectionPicker"
      max-width="520"
      data-test="collection-picker-dialog"
    >
      <v-card>
        <v-card-title>Use in Collection</v-card-title>
        <v-card-text>
          <p class="text-body-2 mb-4">
            Pick a Collection to create a DataObject from
            <strong>{{ selected?.name }}</strong>.
          </p>
          <v-autocomplete
            :model-value="selectedCollection"
            :items="collectionSearchResults.map(mapToItem)"
            :loading="isSearching"
            :hide-no-data="hideNoDataMessage"
            label="Search Collection by name or ID…"
            density="comfortable"
            variant="outlined"
            no-data-text="No matching collections"
            clearable
            color="primary"
            return-object
            hide-details
            data-test="collection-autocomplete"
            @update:model-value="selectedCollection = $event ?? undefined"
            @update:search="onCollectionSearch"
          />
          <v-alert
            v-if="instantiateError"
            type="error"
            variant="tonal"
            density="compact"
            class="mt-3"
            data-test="instantiate-error"
          >
            {{ instantiateError }}
          </v-alert>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn
            variant="text"
            :disabled="isInstantiating"
            @click="showCollectionPicker = false"
          >
            Cancel
          </v-btn>
          <v-btn
            color="primary"
            variant="flat"
            :disabled="!selectedCollection || isInstantiating"
            :loading="isInstantiating"
            data-test="confirm-instantiate-btn"
            @click="confirmInstantiate"
          >
            Create DataObject
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>
