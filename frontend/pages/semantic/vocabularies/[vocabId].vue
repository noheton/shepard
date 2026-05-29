<script setup lang="ts">
// /semantic/vocabularies/[vocabId] — per-vocabulary predicate browser.
//
// Calls GET /v2/semantic/vocabularies/{vocabId}/predicates and renders the
// rows in a sortable, searchable v-data-table. Empty vocabularies render
// a helpful card pointing to the admin Ontology Bundles pane. Missing
// vocabularies (404) render a not-found banner; SPARQL fallback is still
// linked for power users.
//
// Design: aidocs/semantics/100 §4.
// Backlog row: SEMA-V6-UI-FOLLOWUP.

import { useSemanticVocabularyPredicates, type SemanticPredicate } from "~/composables/semantic/useSemanticVocabularyPredicates";

const route = useRoute();
const vocabId = computed(() => String(route.params.vocabId ?? ""));

useHead({ title: () => `${vocabId.value} | vocabularies | shepard` });

const { predicates, loading, error, notFound, fetchPredicates } = useSemanticVocabularyPredicates();
const search = ref("");

const headers = [
  { title: "URI", key: "uri", sortable: true },
  { title: "Label", key: "label", sortable: true },
  { title: "Object type", key: "expectedObjectType", sortable: true },
  { title: "Cardinality", key: "cardinality", sortable: true },
  { title: "Required", key: "required", sortable: true, align: "center" as const },
];

const filtered = computed<SemanticPredicate[]>(() => {
  const q = search.value.trim().toLowerCase();
  if (!q) return predicates.value;
  return predicates.value.filter((p) => {
    const uri = (p.uri ?? "").toLowerCase();
    const label = (p.label ?? "").toLowerCase();
    return uri.includes(q) || label.includes(q);
  });
});

onMounted(() => fetchPredicates(vocabId.value));
watch(vocabId, (next) => fetchPredicates(next));

function predicateRoute(p: SemanticPredicate) {
  return `/semantic/predicates/${encodeURIComponent(p.uri)}`;
}
</script>

<template>
  <v-container>
    <div class="d-flex flex-column ga-2 mb-4">
      <NuxtLink to="/semantic/vocabularies" class="text-caption">
        <v-icon size="small">mdi-arrow-left</v-icon> All vocabularies
      </NuxtLink>
      <h4 class="text-h4">Vocabulary predicates</h4>
      <code class="text-body-2 text-break">{{ vocabId }}</code>
      <p class="text-body-1 text-medium-emphasis">
        Every predicate declared inside this vocabulary. Predicates are the
        annotation properties that can appear on the left-hand side of a
        semantic annotation (e.g. <code>dcterms:creator</code>,
        <code>m4i:realizesMethod</code>).
      </p>
    </div>

    <v-progress-linear v-if="loading" indeterminate class="mb-3" />

    <v-alert
      v-if="notFound && !loading"
      type="warning"
      variant="tonal"
      class="mb-3"
      title="Vocabulary not found"
    >
      No vocabulary with this <code>appId</code> exists in the internal
      semantic store.
      <NuxtLink to="/semantic/vocabularies">Back to the vocabulary list</NuxtLink>.
    </v-alert>

    <v-alert
      v-else-if="error && !loading"
      type="error"
      variant="tonal"
      class="mb-3"
    >
      Failed to load predicates: {{ error }}
    </v-alert>

    <template v-else-if="!loading">
      <v-card v-if="predicates.length === 0" variant="outlined" class="mb-3">
        <v-card-title class="text-subtitle-1">No predicates declared yet</v-card-title>
        <v-card-text>
          <p class="text-body-2 mb-3">
            This vocabulary has no predicates yet. Add some via the admin
            Ontology Bundles pane (upload a TTL/RDF file or refresh a
            git-tracked source), or run a custom
            <NuxtLink to="/semantic/sparql">SPARQL query</NuxtLink>
            against the internal store.
          </p>
          <v-btn
            variant="tonal"
            size="small"
            prepend-icon="mdi-tools"
            to="/admin#ontology-bundles"
          >
            Open admin ontology bundles
          </v-btn>
        </v-card-text>
      </v-card>

      <template v-else>
        <v-text-field
          v-model="search"
          density="compact"
          variant="outlined"
          hide-details
          clearable
          placeholder="Search predicates by URI or label"
          prepend-inner-icon="mdi-magnify"
          class="mb-3"
        />

        <v-data-table
          :headers="headers"
          :items="filtered"
          item-value="appId"
          :items-per-page="25"
          :items-per-page-options="[10, 25, 50, 100]"
          density="comfortable"
        >
          <template #item.uri="{ item }">
            <NuxtLink :to="predicateRoute(item)" class="text-body-2">
              <code>{{ item.uri }}</code>
            </NuxtLink>
          </template>
          <template #item.label="{ item }">
            <span v-if="item.label">{{ item.label }}</span>
            <span v-else class="text-medium-emphasis">—</span>
          </template>
          <template #item.expectedObjectType="{ item }">
            <v-chip v-if="item.expectedObjectType" size="x-small" variant="tonal">
              {{ item.expectedObjectType }}
            </v-chip>
            <span v-else class="text-medium-emphasis">—</span>
          </template>
          <template #item.cardinality="{ item }">
            <v-chip v-if="item.cardinality" size="x-small" variant="outlined">
              {{ item.cardinality }}
            </v-chip>
            <span v-else class="text-medium-emphasis">—</span>
          </template>
          <template #item.required="{ item }">
            <v-icon v-if="item.required" color="warning" size="small">
              mdi-asterisk
            </v-icon>
            <span v-else class="text-medium-emphasis">—</span>
          </template>
        </v-data-table>
      </template>
    </template>
  </v-container>
</template>
