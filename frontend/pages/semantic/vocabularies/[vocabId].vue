<script setup lang="ts">
// /semantic/vocabularies/[vocabId] — vocabulary detail page. Shows the
// bundle metadata and (when SEMA-V6-UI-FOLLOWUP ships a predicate-listing
// endpoint) a predicate table. Until then, falls back to a SPARQL-link
// hint per advisor guidance.
//
// Design: aidocs/semantics/100 §4.

import PlaceholderImplStatus from "~/components/common/placeholder/PlaceholderImplStatus.vue";
import PlaceholderRestDump from "~/components/common/placeholder/PlaceholderRestDump.vue";

const route = useRoute();
const vocabId = computed(() => String(route.params.vocabId ?? ""));

useHead({ title: () => `${vocabId.value} | vocabularies | shepard` });

// Until the backend slice ships, the per-vocabulary predicate listing
// must be reached via SPARQL (link below) or via the admin bundle GET.
const adminEndpoint = computed(
  () => `/v2/admin/semantic/ontologies`,
);
</script>

<template>
  <v-container>
    <div class="d-flex flex-column ga-2 mb-4">
      <NuxtLink to="/semantic/vocabularies" class="text-caption">
        <v-icon size="small">mdi-arrow-left</v-icon> All vocabularies
      </NuxtLink>
      <h4 class="text-h4">{{ vocabId }}</h4>
      <p class="text-body-1 text-medium-emphasis">
        Predicates contributed by this vocabulary. Until the dedicated
        predicate-listing endpoint ships (SEMA-V6-UI-FOLLOWUP), enumerate
        predicates by running
        <code>SELECT DISTINCT ?p WHERE { ?s ?p ?o }</code> via the
        <NuxtLink to="/semantic/sparql">SPARQL playground</NuxtLink>,
        scoped to the bundle's IRI prefix.
      </p>
    </div>
    <PlaceholderImplStatus
      backend="partial"
      backlog-row="SEMA-V6-UI-FOLLOWUP"
      design-doc="aidocs/semantics/100-consistent-semantic-annotation-design.md"
      notes="Per-vocabulary predicate-listing endpoint is not yet a first-class REST surface. SPARQL is the fallback."
    />
    <PlaceholderRestDump
      :endpoint="adminEndpoint"
      hint="Admin REST listing of all bundles (find this one by id; instance-admin only)."
    />
  </v-container>
</template>
