<script setup lang="ts">
// /semantic/predicates/[predicateIri] — predicate detail / usage stats.
// Until a dedicated usage-counts endpoint ships (SEMA-V6-UI-FOLLOWUP),
// this page advertises the SPARQL fallback and renders the IRI cleanly
// for deep-link sharing.
//
// Design: aidocs/semantics/100 §5.

import PlaceholderImplStatus from "~/components/common/placeholder/PlaceholderImplStatus.vue";

const route = useRoute();
const predicateIri = computed(() => decodeURIComponent(String(route.params.predicateIri ?? "")));

useHead({ title: () => `${predicateIri.value} | predicates | shepard` });

const sampleQuery = computed(
  () =>
    `SELECT (COUNT(*) AS ?usageCount) WHERE { ?s <${predicateIri.value}> ?o }`,
);
</script>

<template>
  <v-container>
    <div class="d-flex flex-column ga-2 mb-4">
      <NuxtLink to="/semantic/vocabularies" class="text-caption">
        <v-icon size="small">mdi-arrow-left</v-icon> Vocabularies
      </NuxtLink>
      <h4 class="text-h4">Predicate</h4>
      <code class="text-body-2 text-break">{{ predicateIri }}</code>
      <p class="text-body-1 text-medium-emphasis mt-2">
        Usage stats and sample values are queryable today via the
        <NuxtLink to="/semantic/sparql">SPARQL playground</NuxtLink>.
        A dedicated read-only endpoint is queued under SEMA-V6-UI-FOLLOWUP.
      </p>
    </div>
    <v-card variant="outlined" class="mb-3">
      <v-card-title class="text-subtitle-1">Suggested query</v-card-title>
      <v-card-text>
        <pre class="text-caption">{{ sampleQuery }}</pre>
        <v-btn
          variant="tonal"
          size="small"
          prepend-icon="mdi-play"
          :to="{ path: '/semantic/sparql', query: { query: sampleQuery } }"
        >
          Open in SPARQL playground
        </v-btn>
      </v-card-text>
    </v-card>
    <PlaceholderImplStatus
      backend="partial"
      backlog-row="SEMA-V6-UI-FOLLOWUP"
      design-doc="aidocs/semantics/100-consistent-semantic-annotation-design.md"
      notes="Per-predicate usage-stats endpoint not shipped; SPARQL fallback above."
    />
  </v-container>
</template>
