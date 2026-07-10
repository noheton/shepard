<script setup lang="ts">
/**
 * TPL-ME-BROWSE-1 — non-admin browse-mine surface for ShepardTemplate.
 * TPL-ME-USE-FROM-BROWSE — "Use here…" affordance: pick a Collection and
 * instantiate the template directly, without navigating to a Collection first.
 *
 * Backend (`GET /v2/templates`, `ShepardTemplateRest`) is readable by any
 * authenticated user; only POST / PATCH / DELETE are gated to
 * `instance-admin`. Pre-this pane, researchers could only see what
 * templates exist via the picker dropdown in `CreateDataObjectDialog` —
 * which requires already being mid-create-flow inside a Collection.
 * The Reluctant Senior Researcher persona wanted a flat "what's on this
 * instance" answer; this pane is that.
 */
import {
  CollectionTemplatesApi,
  TemplatesApi,
  type Collection,
  type ShepardTemplate,
} from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";
import { useFetchRecentCollections } from "~/composables/context/useFetchRecentCollections";
import { readCollectionAppId, readDataObjectAppId } from "~/utils/appId";

const router = useRouter();

const templates = ref<ShepardTemplate[]>([]);
const isLoading = ref(false);
const loadError = ref<string | null>(null);
const filter = ref("");
const selected = ref<ShepardTemplate | null>(null);
const showDetails = ref(false);

// ── "Use here…" dialog state ──────────────────────────────────────────────────

const showUseDialog = ref(false);
const useTargetCollection = ref<Collection | null>(null);
const isInstantiating = ref(false);
const useError = ref<string | null>(null);

const { collections: recentCollections, loading: collectionsLoading } =
  useFetchRecentCollections();

/** Collections that carry a v2 appId (needed for instantiation route). */
// UIRULE-DROPDOWN-SEARCH-SORT: searchable (v-autocomplete); order is deliberately
// the source recency order (recentCollections), NOT natural-sorted.
const pickableCollections = computed(() =>
  recentCollections.value.filter(c => !!readCollectionAppId(c)),
);

function openUseDialog() {
  useTargetCollection.value = null;
  useError.value = null;
  showUseDialog.value = true;
}

async function confirmUse() {
  if (!selected.value?.appId || !useTargetCollection.value) return;
  const collectionAppId = readCollectionAppId(useTargetCollection.value);
  if (!collectionAppId) return;

  isInstantiating.value = true;
  useError.value = null;
  try {
    const created = await useV2ShepardApi(CollectionTemplatesApi)
      .value.instantiateDataObject({
        collectionAppId,
        templateAppId: selected.value.appId,
      });

    // Navigate to the new DataObject; both ids carry the v2 appId.
    const doAppId = readDataObjectAppId(created);
    await router.push(
      collectionsPath +
        collectionAppId +
        dataObjectsPathFragment +
        (doAppId ?? created.id),
    );
    showUseDialog.value = false;
    showDetails.value = false;
  } catch (e) {
    const status = (e as { response?: { status?: number } })?.response?.status;
    if (status === 403) {
      useError.value = "You don't have write permission on that Collection.";
    } else if (status === 404) {
      useError.value = "Template or Collection not found — the list may be stale.";
    } else {
      useError.value = "Instantiation failed — please try again.";
      handleError(e, "instantiateDataObject from browse");
    }
  } finally {
    isInstantiating.value = false;
  }
}

// ── Template list ─────────────────────────────────────────────────────────────

function load() {
  isLoading.value = true;
  loadError.value = null;
  useV2ShepardApi(TemplatesApi)
    .value.listTemplates({ pageSize: 200 })
    .then(page => {
      templates.value = page?.items ?? [];
    })
    .catch(err => {
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
      Browse every <code>ShepardTemplate</code> available on this Shepard. Click
      <strong>Use here…</strong> to instantiate a template directly into one of
      your Collections without navigating away.
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

    <!-- Details dialog — body preview + Use here… action -->
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
          <v-spacer />
          <v-btn variant="text" @click="showDetails = false">Close</v-btn>
          <v-btn
            v-if="selected.appId"
            color="primary"
            variant="tonal"
            prepend-icon="mdi-plus-circle-outline"
            data-test="template-use-btn"
            @click="openUseDialog"
          >
            Use here…
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- Collection-picker dialog — picks destination + calls instantiateDataObject -->
    <v-dialog v-model="showUseDialog" max-width="560" persistent>
      <v-card>
        <v-card-title class="text-h6">
          Use "{{ selected?.name }}" in a Collection
        </v-card-title>
        <v-card-text>
          <p class="text-body-2 text-medium-emphasis mb-4">
            Pick one of your Collections. A new DataObject will be created
            from this template and you'll be taken there.
          </p>

          <v-autocomplete
            v-model="useTargetCollection"
            :items="pickableCollections"
            :loading="collectionsLoading"
            item-title="name"
            item-value="id"
            return-object
            label="Collection"
            placeholder="Type to search your collections"
            prepend-inner-icon="mdi-folder-outline"
            variant="outlined"
            density="compact"
            no-data-text="No accessible collections found"
            data-test="use-collection-picker"
          />

          <v-alert
            v-if="useError"
            type="error"
            variant="tonal"
            density="compact"
            class="mt-3"
            data-test="use-error"
          >
            {{ useError }}
          </v-alert>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn
            variant="text"
            :disabled="isInstantiating"
            @click="showUseDialog = false"
          >
            Cancel
          </v-btn>
          <v-btn
            color="primary"
            variant="flat"
            :loading="isInstantiating"
            :disabled="!useTargetCollection || isInstantiating"
            data-test="use-confirm-btn"
            @click="confirmUse"
          >
            Create DataObject
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>
