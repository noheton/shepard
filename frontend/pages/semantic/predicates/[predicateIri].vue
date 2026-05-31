<script setup lang="ts">
// /semantic/predicates/[predicateIri] — predicate detail / usage stats.
//
// Replaces the previous SPARQL-fallback stub (which advertised a queued
// SEMA-V6-UI-FOLLOWUP endpoint). The page now renders real aggregates from
// `GET /v2/semantic/predicates/{predicateIriBase64}/stats`
// (SemanticPredicateStatsRest, shipped under SEMA-V6-PRED-UI):
//
//   • Total annotations carrying this predicate.
//   • Top object values (frequency desc; literals + IRIs both supported).
//   • Sample of annotated entities (10 by default, with type chips).
//
// A "Open in SPARQL playground" affordance is kept as the power-user
// escape hatch — but it's no longer the only way to ask the question.
//
// Design: aidocs/semantics/100 §5. Backlog: SEMA-V6-PRED-UI.
import { usePredicateStats } from "~/composables/semantic/usePredicateStats";

const route = useRoute();
const predicateIri = computed(() => decodeURIComponent(String(route.params.predicateIri ?? "")));

useHead({ title: () => `${predicateIri.value} | predicates | shepard` });

const { stats, loading, error } = usePredicateStats(predicateIri, {
  topValuesLimit: 20,
  sampleLimit: 10,
});

const sampleQuery = computed(
  () => `SELECT (COUNT(*) AS ?usageCount) WHERE { ?s <${predicateIri.value}> ?o }`,
);

function entityHref(s: { appId: string; type?: string | null }): string | null {
  // Most entity kinds aren't yet wired into a top-level detail URL accepting
  // a bare appId; we surface the appId as a copyable token and link to the
  // SPARQL playground for richer exploration. As more `/entities/{appId}`
  // resolvers ship, this function gets a per-`type` branch.
  if (!s.appId) return null;
  const q = `SELECT ?p ?o WHERE { <urn:shepard:appId:${s.appId}> ?p ?o }`;
  return `/semantic/sparql?query=${encodeURIComponent(q)}`;
}

function topValueDisplay(v: { objectIri?: string | null; objectLabel?: string | null }): string {
  if (v.objectLabel && v.objectLabel.length > 0) return v.objectLabel;
  if (v.objectIri && v.objectIri.length > 0) return v.objectIri;
  return "(empty)";
}
</script>

<template>
  <v-container>
    <div class="d-flex flex-column ga-2 mb-4">
      <NuxtLink to="/semantic/vocabularies" class="text-caption">
        <v-icon size="small">mdi-arrow-left</v-icon> Vocabularies
      </NuxtLink>
      <h4 class="text-h4">Predicate</h4>
      <code class="text-body-2 text-break">{{ predicateIri }}</code>
    </div>

    <!-- Loading / error / empty states -->
    <div v-if="loading" class="pa-4 text-medium-emphasis text-body-2">
      Loading predicate statistics…
    </div>
    <v-alert v-else-if="error" type="warning" variant="tonal" class="mb-3">
      Could not load predicate statistics: {{ error }}
    </v-alert>

    <template v-else-if="stats">
      <!-- Total annotations stat card -->
      <v-card variant="outlined" class="mb-3" data-testid="predicate-total-card">
        <v-card-text class="d-flex align-center ga-3">
          <v-icon size="32" color="primary">mdi-counter</v-icon>
          <div>
            <div class="text-h5">{{ stats.annotationCount.toLocaleString() }}</div>
            <div class="text-caption text-medium-emphasis">
              Total annotations using this predicate
            </div>
          </div>
        </v-card-text>
      </v-card>

      <!-- Top values table -->
      <v-card variant="outlined" class="mb-3">
        <v-card-title class="text-subtitle-1">Top values</v-card-title>
        <v-card-text class="pa-0">
          <div
            v-if="stats.topValues.length === 0"
            class="pa-4 text-body-2 text-medium-emphasis"
          >
            No annotations yet. Once an entity is annotated with this predicate,
            its value will appear here.
          </div>
          <v-table v-else density="compact" data-testid="top-values-table">
            <thead>
              <tr>
                <th>Value</th>
                <th>IRI</th>
                <th class="text-right">Count</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(v, i) in stats.topValues" :key="`${v.objectIri ?? ''}|${v.objectLabel ?? ''}|${i}`">
                <td class="font-weight-medium">{{ topValueDisplay(v) }}</td>
                <td>
                  <code v-if="v.objectIri" class="text-caption text-break">{{ v.objectIri }}</code>
                  <span v-else class="text-medium-emphasis text-caption">literal</span>
                </td>
                <td class="text-right">{{ v.count.toLocaleString() }}</td>
              </tr>
            </tbody>
          </v-table>
        </v-card-text>
      </v-card>

      <!-- Sample entities table -->
      <v-card variant="outlined" class="mb-3">
        <v-card-title class="text-subtitle-1">Sample annotated entities</v-card-title>
        <v-card-text class="pa-0">
          <div
            v-if="stats.sampleEntities.length === 0"
            class="pa-4 text-body-2 text-medium-emphasis"
          >
            No entities carry this annotation yet.
          </div>
          <v-table v-else density="compact" data-testid="sample-entities-table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Type</th>
                <th>appId</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="s in stats.sampleEntities" :key="s.appId">
                <td class="font-weight-medium">
                  <NuxtLink v-if="entityHref(s)" :to="entityHref(s)!">
                    {{ s.name ?? s.appId }}
                  </NuxtLink>
                  <span v-else>{{ s.name ?? s.appId }}</span>
                </td>
                <td>
                  <v-chip v-if="s.type" size="x-small" variant="outlined">{{ s.type }}</v-chip>
                  <span v-else class="text-medium-emphasis text-caption">unknown</span>
                </td>
                <td><code class="text-caption">{{ s.appId }}</code></td>
              </tr>
            </tbody>
          </v-table>
        </v-card-text>
      </v-card>
    </template>

    <!-- Power-user escape hatch: keep the SPARQL link, but it's no longer the
         primary way to ask "how is this predicate used". -->
    <v-card variant="outlined" class="mb-3">
      <v-card-title class="text-subtitle-1">Open in SPARQL playground</v-card-title>
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
  </v-container>
</template>
