<script setup lang="ts">
// /semantic/vocabularies — vocabulary index. Lists every :Vocabulary node
// seeded into the internal semantic store via the read-only browse
// endpoint GET /v2/semantic/vocabularies (SEMA-V6-UI-FOLLOWUP). Drill
// into a vocabulary detail page for predicate listings.
//
// Design: aidocs/semantics/100 §4.

import { buildVocabBrowserUrl } from "~/utils/vocabBrowserUrl";

interface VocabularyRow {
  appId?: string;
  uri?: string;
  label?: string;
  prefix?: string;
  description?: string;
  enabled?: boolean;
  type?: string;
}

useHead({ title: "Vocabularies | semantic | shepard" });

// TOOLS-CONTEXT-{COLL,DO}-VOCAB — when arriving from an in-context Tools
// menu the URL carries `?usedBy=<entityAppId>&scope=collection|data-object`.
// We then call the dedicated backend filter endpoint
// `GET /v2/semantic/vocabularies/used-by/{appId}?scope=…`
// (TOOLS-CONTEXT-VOCAB-BACKEND-1, landed 2026-05-30) so the list shows only
// vocabularies whose terms appear in this entity's :SemanticAnnotation set.
const route = useRoute();
const router = useRouter();
const usedByAppId = computed<string | null>(() =>
  typeof route.query.usedBy === "string" ? route.query.usedBy : null,
);
const usedByScope = computed<string | null>(() =>
  typeof route.query.scope === "string" ? route.query.scope : null,
);

const vocabularies = ref<VocabularyRow[]>([]);
const isLoading = ref(false);
const error = ref<string | null>(null);

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

async function loadVocabularies() {
  isLoading.value = true;
  error.value = null;
  try {
    const { data: auth } = useAuth();
    const headers: Record<string, string> = { Accept: "application/json" };
    if (auth.value?.accessToken) {
      headers["Authorization"] = `Bearer ${auth.value.accessToken}`;
    }
    // TOOLS-CONTEXT-VOCAB-BACKEND-1 — when ?usedBy is present, use the
    // filtered endpoint. Otherwise fall back to the full inventory.
    const url = buildVocabBrowserUrl(v2BaseUrl(), usedByAppId.value, usedByScope.value);
    const res = await fetch(url, { headers });
    if (!res.ok) {
      error.value = `${res.status} ${res.statusText}`;
      return;
    }
    const body = await res.json();
    // GET /v2/semantic/vocabularies may return either a bare array or the
    // PagedResponseIO {items,...} envelope (APISIMP-PAGINATION-ENVELOPE).
    // Tolerate both so a partial deploy never blanks the list.
    vocabularies.value = Array.isArray(body)
      ? body
      : Array.isArray(body?.items)
        ? body.items
        : [];
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : String(e);
  } finally {
    isLoading.value = false;
  }
}

function clearFilter() {
  void router.push({ path: "/semantic/vocabularies" });
}

// Re-load whenever the filter param changes (router.push between filtered
// and unfiltered views is in-page, not a full mount).
watch([usedByAppId, usedByScope], () => {
  void loadVocabularies();
});

onMounted(loadVocabularies);
</script>

<template>
  <v-container>
    <div class="d-flex flex-column ga-2 mb-4">
      <div class="d-flex align-center justify-space-between flex-wrap ga-2">
        <h4 class="text-h4">Vocabularies</h4>
        <v-btn
          to="/semantic/search"
          variant="tonal"
          color="primary"
          size="small"
          prepend-icon="mdi-magnify"
          data-testid="vocab-search-link"
        >
          Search ontology terms
        </v-btn>
      </div>
      <p class="text-body-1 text-medium-emphasis">
        Controlled vocabularies seeded into the internal semantic store.
        Each vocabulary groups one or more predicates available to the
        semantic-annotation picker.
      </p>
    </div>
    <v-alert
      v-if="usedByAppId"
      type="info"
      variant="tonal"
      density="compact"
      class="mb-3"
      prepend-icon="mdi-filter-outline"
      data-testid="vocab-usedby-banner"
    >
      <div class="d-flex align-center ga-2 flex-wrap">
        <span>
          Filtered to terms used by
          {{ usedByScope === "collection" ? "Collection" : "DataObject" }}
          <code>{{ usedByAppId }}</code>.
        </span>
        <v-btn
          size="x-small"
          variant="tonal"
          color="primary"
          data-testid="vocab-usedby-clear"
          @click="clearFilter"
        >
          Clear filter
        </v-btn>
      </div>
    </v-alert>

    <v-progress-linear v-if="isLoading" indeterminate />
    <v-alert v-if="error" type="error" variant="tonal" class="mb-3">
      {{ error }}
    </v-alert>
    <v-list v-if="vocabularies.length > 0">
      <v-list-item
        v-for="v in vocabularies"
        :key="v.appId ?? v.uri"
        :to="`/semantic/vocabularies/${encodeURIComponent(v.appId ?? '')}`"
      >
        <template #prepend>
          <v-icon>mdi-library-outline</v-icon>
        </template>
        <v-list-item-title>
          {{ v.label ?? v.prefix ?? v.uri }}
          <v-chip
            v-if="v.enabled === false"
            size="x-small"
            color="warning"
            class="ml-2"
          >disabled</v-chip>
          <v-chip
            v-if="v.type === 'PERSONAL'"
            size="x-small"
            color="info"
            class="ml-2"
          >personal</v-chip>
        </v-list-item-title>
        <v-list-item-subtitle>
          <code class="text-caption">{{ v.uri }}</code>
        </v-list-item-subtitle>
      </v-list-item>
    </v-list>
    <v-alert
      v-else-if="!isLoading && !error && usedByAppId"
      type="info"
      variant="tonal"
      data-testid="vocab-usedby-empty"
      text="No vocabularies match this filter — the selected entity has no semantic annotations carrying a vocabulary back-pointer. Clear the filter to see the full catalogue."
    />
    <v-alert
      v-else-if="!isLoading && !error"
      type="info"
      variant="tonal"
      text="No vocabularies are currently seeded. Instance-admins can upload bundles via the Semantic Repositories admin pane."
    />
  </v-container>
</template>
