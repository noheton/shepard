<script setup lang="ts">
/**
 * AdminOntologyAlignmentPane (PLACEHOLDER-REPLACE-TPL3a-lite)
 *
 * Replaces the PlaceholderFragmentPane (slug "ontology-alignment") at
 * /admin#ontology-alignment. Read-only table: how core Shepard concepts
 * map to upper-ontology classes (BFO 2020, IAO, PROV-O, IOF Core).
 *
 * Per the "tool entry points are in-context first" rule, the per-row
 * SPARQL button deep-links into /semantic/sparql?query=<prefilled> with
 * a query that finds annotations using the upper-ontology predicate.
 *
 * Diff-view across upstream-bundle versions is deferred — filed as
 * follow-up in aidocs/16 if needed; the brief allows scoping it down.
 */
import { AdminFragments } from "./adminMenuItems";
import { useOntologyAlignment } from "~/composables/context/admin/useOntologyAlignment";
import type { OntologyAlignmentIO } from "~/composables/context/admin/useOntologyAlignment";

const { alignments, isLoading, error, refresh } = useOntologyAlignment();

const headers = [
  { title: "Shepard concept", key: "shepardConcept", sortable: true },
  { title: "Upper-ontology IRI", key: "upperOntologyUri", sortable: true },
  { title: "Alignment kind", key: "relationshipType", sortable: true },
  { title: "Confidence", key: "confidence", sortable: true },
  { title: "Source", key: "source", sortable: false },
  { title: "SPARQL", key: "actions", sortable: false, width: 90 },
] as const;

const confidenceColor = (c: string): string => {
  switch (c?.toUpperCase()) {
    case "HIGH":
      return "success";
    case "MEDIUM":
      return "warning";
    case "LOW":
      return "error";
    default:
      return "default";
  }
};

/**
 * Build a SPARQL query that surfaces RDF triples using the upper-ontology
 * predicate. Returns a query string ready to paste into the playground.
 *
 * For rdfs:subClassOf / owl:equivalentClass alignments, we surface a
 * SELECT of (subject, predicate, object) limited to the mapped IRI.
 */
function buildSparqlQuery(row: OntologyAlignmentIO): string {
  return [
    "# Surface triples using the upper-ontology class this Shepard concept",
    `# (${row.shepardConcept}) is aligned to via ${row.relationshipType}.`,
    `# Source: ${row.source}`,
    "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>",
    "PREFIX owl: <http://www.w3.org/2002/07/owl#>",
    "",
    "SELECT ?s ?p ?o WHERE {",
    `  ?s ?p <${row.upperOntologyUri}> .`,
    "  OPTIONAL { ?s ?p ?o }",
    "}",
    "LIMIT 100",
  ].join("\n");
}

function sparqlLink(row: OntologyAlignmentIO): {
  path: string;
  query: Record<string, string>;
} {
  return {
    path: "/semantic/sparql",
    query: { query: buildSparqlQuery(row) },
  };
}
</script>

<template>
  <div :id="AdminFragments.ONTOLOGY_ALIGNMENT" class="d-flex flex-column ga-4">
    <div class="d-flex align-center justify-space-between flex-wrap ga-2">
      <div class="d-flex align-center ga-3">
        <h4 class="text-h4">Ontology Alignment Registry</h4>
        <v-btn
          icon="mdi-refresh"
          variant="text"
          size="small"
          :loading="isLoading"
          aria-label="Refresh"
          @click="refresh"
        />
      </div>
      <v-chip size="small" variant="tonal" color="info">
        {{ alignments.length }} alignment{{ alignments.length === 1 ? "" : "s" }}
      </v-chip>
    </div>

    <p class="text-body-2 text-medium-emphasis">
      Read-only registry of how core Shepard concepts map onto upper-ontology
      classes (BFO 2020, IAO, PROV-O, IOF Core). Seeded by the V67 Neo4j
      migration and refreshed automatically when ontology bundles change.
      Mapping authority:
      <code>aidocs/semantics/96-upper-ontology-alignment.md</code>; operator
      runbook: <code>aidocs/semantics/97</code>.
    </p>

    <v-alert
      v-if="error"
      type="error"
      variant="tonal"
      closable
      @click:close="error = null"
    >
      {{ error }}
    </v-alert>

    <v-progress-linear v-if="isLoading && alignments.length === 0" indeterminate />

    <v-card v-if="alignments.length > 0" variant="outlined">
      <v-data-table
        :headers="headers as unknown as []"
        :items="alignments"
        item-value="appId"
        density="comfortable"
        :items-per-page="20"
        class="ontology-alignment-table"
      >
        <template #[`item.shepardConcept`]="{ item }">
          <code class="text-body-2 font-weight-medium">{{ item.shepardConcept }}</code>
        </template>

        <template #[`item.upperOntologyUri`]="{ item }">
          <a
            :href="item.upperOntologyUri"
            target="_blank"
            rel="noopener noreferrer"
            class="text-decoration-none"
          >
            <code class="text-body-2">{{ item.upperOntologyUri }}</code>
            <v-icon size="x-small" class="ml-1">mdi-open-in-new</v-icon>
          </a>
        </template>

        <template #[`item.relationshipType`]="{ item }">
          <v-chip size="x-small" variant="tonal">
            {{ item.relationshipType }}
          </v-chip>
        </template>

        <template #[`item.confidence`]="{ item }">
          <v-chip
            size="x-small"
            :color="confidenceColor(item.confidence)"
            variant="tonal"
          >
            {{ item.confidence }}
          </v-chip>
        </template>

        <template #[`item.source`]="{ item }">
          <span class="text-caption text-medium-emphasis">{{ item.source }}</span>
        </template>

        <template #[`item.actions`]="{ item }">
          <v-btn
            :to="sparqlLink(item)"
            size="small"
            variant="text"
            prepend-icon="mdi-database-search-outline"
            aria-label="Open SPARQL deep-link"
          >
            SPARQL
          </v-btn>
        </template>
      </v-data-table>
    </v-card>

    <v-card
      v-else-if="!isLoading && !error"
      variant="outlined"
      class="pa-6 text-center text-body-2 text-medium-emphasis"
    >
      No alignments registered. The V67 Neo4j migration normally seeds the
      core 12 mappings — check that the migration ran (see
      <code>aidocs/semantics/97</code>).
    </v-card>

    <div class="text-caption text-medium-emphasis">
      Each SPARQL deep-link opens
      <code>/semantic/sparql</code> with a pre-filled query that returns
      triples using the aligned upper-ontology IRI. No backend mutations are
      possible from this page — alignments are added via the ontology-bundle
      upload pipeline.
    </div>
  </div>
</template>
