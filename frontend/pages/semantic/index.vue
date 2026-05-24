<script setup lang="ts">
// /semantic — top-level landing for the semantic substrate browser.
// Headline of the no-UI-gap roll-out (2026-05-24); the four child routes
// (vocabularies, vocabulary detail, predicate detail, sparql) are reached
// from the cards below. See aidocs/semantics/100-consistent-semantic-
// annotation-design.md §4–§5.

useHead({
  title: "Semantic | shepard",
});

const cards = [
  {
    to: "/semantic/vocabularies",
    icon: "mdi-library-outline",
    title: "Vocabularies",
    description:
      "Browse the ontology bundles seeded into this instance and the predicates they expose.",
  },
  {
    to: "/semantic/sparql",
    icon: "mdi-database-search-outline",
    title: "SPARQL query",
    description:
      "Run read-only SPARQL SELECT/ASK queries against the internal n10s-managed graph.",
  },
];
</script>

<template>
  <v-container>
    <div class="d-flex flex-column ga-2 mb-4">
      <h4 class="text-h4">Semantic substrate</h4>
      <p class="text-body-1 text-medium-emphasis">
        Vocabularies, predicates, and SPARQL access to the internal knowledge
        graph. Design source:
        <a
          href="https://github.com/noheton/shepard/blob/main/aidocs/semantics/100-consistent-semantic-annotation-design.md"
          target="_blank"
          rel="noopener"
        >SEMA-V6 (aidocs/semantics/100)</a>.
      </p>
    </div>
    <v-row>
      <v-col v-for="c in cards" :key="c.to" cols="12" md="6">
        <v-card :to="c.to" hover>
          <v-card-title>
            <v-icon class="mr-2">{{ c.icon }}</v-icon>{{ c.title }}
          </v-card-title>
          <v-card-text>{{ c.description }}</v-card-text>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>
