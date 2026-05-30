<script setup lang="ts">
// /semantic/vocabularies — vocabulary index. Lists every :Vocabulary node
// seeded into the internal semantic store via the read-only browse
// endpoint GET /v2/semantic/vocabularies (SEMA-V6-UI-FOLLOWUP). Drill
// into a vocabulary detail page for predicate listings.
//
// Design: aidocs/semantics/100 §4.

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
// The backend filter ("show only vocabularies whose terms are referenced
// by this entity") is queued as TOOLS-CONTEXT-VOCAB-BACKEND-1; until it
// ships we render a banner explaining that the filter is pending so the
// user understands why the full list is shown.
const route = useRoute();
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
    const res = await fetch(v2BaseUrl() + "/v2/semantic/vocabularies", { headers });
    if (!res.ok) {
      error.value = `${res.status} ${res.statusText}`;
      return;
    }
    const body = await res.json();
    vocabularies.value = Array.isArray(body) ? body : [];
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : String(e);
  } finally {
    isLoading.value = false;
  }
}

onMounted(loadVocabularies);
</script>

<template>
  <v-container>
    <div class="d-flex flex-column ga-2 mb-4">
      <h4 class="text-h4">Vocabularies</h4>
      <p class="text-body-1 text-medium-emphasis">
        Controlled vocabularies seeded into the internal semantic store.
        Each vocabulary groups one or more predicates available to the
        semantic-annotation picker.
      </p>
    </div>
    <v-alert
      v-if="usedByAppId"
      type="warning"
      variant="tonal"
      density="compact"
      class="mb-3"
      prepend-icon="mdi-filter-outline"
      data-testid="vocab-usedby-banner"
    >
      Filter pending: requested vocabularies used by
      {{ usedByScope === "collection" ? "Collection" : "DataObject" }}
      <code>{{ usedByAppId }}</code>. The backend filter (TOOLS-CONTEXT-VOCAB-BACKEND-1)
      is queued — full inventory shown for now.
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
      v-else-if="!isLoading && !error"
      type="info"
      variant="tonal"
      text="No vocabularies are currently seeded. Instance-admins can upload bundles via the Semantic Repositories admin pane."
    />
  </v-container>
</template>
