<script setup lang="ts">
/**
 * SemanticPane — `/me#semantic` profile fragment.
 *
 * SEMA-NAV-PLACEMENT-DECISION option (b), 2026-05-24: the semantic
 * substrate's discovery affordance moved from a top-level header link
 * into a profile fragment, treating semantic browsing as per-user
 * research tooling. Standalone /semantic/* routes (introduced by the
 * no-UI-gap placeholder PR `8c682955`) remain valid for bookmarking +
 * sharing; this pane is the entry point.
 *
 * Cites aidocs/semantics/100 (SEMA-V6 SSOT) for the underlying surface.
 */

interface QuickLink {
  to: string;
  title: string;
  subtitle: string;
  icon: string;
}

const quickLinks: QuickLink[] = [
  {
    to: "/semantic/vocabularies",
    title: "Vocabularies",
    subtitle: "Browse the loaded ontologies + predicate inventory",
    icon: "mdi-bookshelf",
  },
  {
    to: "/semantic/sparql",
    title: "SPARQL playground",
    subtitle: "Query the semantic graph directly",
    icon: "mdi-code-braces",
  },
  {
    to: "/shapes/validate",
    title: "Shape validator",
    subtitle: "Run SHACL conformance against shape definitions",
    icon: "mdi-check-decagram-outline",
  },
  {
    to: "/snapshots/diff",
    title: "Snapshot diff",
    subtitle: "Compare collection snapshots across time",
    icon: "mdi-vector-difference",
  },
];
</script>

<template>
  <v-container class="pa-0">
    <div class="text-h5 font-weight-medium mb-2">Semantic substrate</div>
    <div class="text-body-1 text-medium-emphasis mb-6">
      Research tooling for the shepard semantic substrate — browse vocabularies,
      query the graph with SPARQL, validate shape conformance, and compare
      snapshots. The full annotation surface is being designed in
      <a
        href="https://github.com/noheton/shepard/blob/main/aidocs/semantics/100-consistent-semantic-annotation-design.md"
        target="_blank"
        rel="noopener"
      >SEMA-V6</a>.
    </div>

    <v-row>
      <v-col
        v-for="link in quickLinks"
        :key="link.to"
        cols="12"
        sm="6"
      >
        <v-card
          :to="link.to"
          variant="outlined"
          class="h-100"
          :data-testid="`semantic-link-${link.to.replace(/\//g, '-')}`"
        >
          <v-card-title class="d-flex align-center ga-3">
            <v-icon :icon="link.icon" size="28" />
            <span>{{ link.title }}</span>
          </v-card-title>
          <v-card-text>
            {{ link.subtitle }}
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>
