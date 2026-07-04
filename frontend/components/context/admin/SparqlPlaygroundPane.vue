<script setup lang="ts">
// N1f — SPARQL playground admin pane.
// Backed by POST /v2/semantic/{repoAppId}/sparql (application/x-www-form-urlencoded).
// Results rendered as v-data-table from W3C SPARQL Results JSON format.
// Read-only enforcement (SELECT / ASK only) is handled server-side by
// SparqlQueryValidator; the UI reflects whatever error the server returns.

import { AdminFragments } from "./adminMenuItems";
import type { SparqlBinding } from "~/composables/context/admin/useSparqlPlayground";
import { useSparqlPlayground } from "~/composables/context/admin/useSparqlPlayground";

const {
  repoId,
  query,
  isLoading,
  error,
  results,
  rowCount,
  runQuery,
  resetQuery,
} = useSparqlPlayground();

// Derived table headers from SPARQL result variables.
const tableHeaders = computed(() => {
  if (!results.value) return [];
  return results.value.head.vars.map((v: string) => ({
    title: `?${v}`,
    key: v,
    sortable: true,
  }));
});

// Flatten SPARQL bindings to plain rows for v-data-table.
const tableItems = computed<Record<string, string>[]>(() => {
  if (!results.value) return [];
  return results.value.results.bindings.map((binding: SparqlBinding) => {
    const row: Record<string, string> = {};
    for (const v of results.value!.head.vars) {
      const cell = binding[v];
      row[v] = cell ? cell.value : "";
    }
    return row;
  });
});


</script>

<template>
  <div :id="AdminFragments.SPARQL_PLAYGROUND" class="d-flex flex-column ga-4">
    <!-- Header -->
    <div class="d-flex align-center justify-space-between flex-wrap ga-2">
      <div class="d-flex align-center ga-3">
        <h4 class="text-h4">SPARQL Playground</h4>
      </div>
      <div class="d-flex ga-2">
        <v-btn
          variant="text"
          size="small"
          prepend-icon="mdi-refresh"
          :disabled="isLoading"
          @click="resetQuery"
        >
          Reset
        </v-btn>
        <v-btn
          color="primary"
          variant="tonal"
          prepend-icon="mdi-play"
          :loading="isLoading"
          @click="runQuery"
        >
          Run query
        </v-btn>
      </div>
    </div>

    <p class="text-body-2 text-medium-emphasis">
      Execute read-only SPARQL <code>SELECT</code> or <code>ASK</code> queries
      against any configured semantic repository. Mutation forms
      (<code>INSERT</code>, <code>DELETE</code>, <code>UPDATE</code>, …) are
      rejected server-side. Endpoint:
      <code>POST /v2/semantic/{'{'}repoAppId{'}'}/sparql</code>.
    </p>

    <!-- Repository ID field -->
    <v-text-field
      v-model="repoId"
      label="Repository ID (appId)"
      density="compact"
      variant="outlined"
      hint="Use the appId shown in the Semantic Repositories panel. Default 'internal' targets the n10s-managed Neo4j store."
      persistent-hint
      class="mb-1"
      style="max-width: 480px"
    />

    <!-- SPARQL query editor -->
    <v-textarea
      v-model="query"
      label="SPARQL query"
      variant="outlined"
      :rows="10"
      auto-grow
      class="sparql-editor mb-1"
      placeholder="SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 10"
      @keydown.ctrl.enter.prevent="runQuery"
    />
    <div class="text-caption text-medium-emphasis mb-2">
      Tip: press <kbd>Ctrl</kbd>+<kbd>Enter</kbd> to run.
    </div>

    <!-- Error display -->
    <v-alert
      v-if="error"
      type="error"
      variant="tonal"
      closable
      @click:close="error = null"
    >
      <div class="text-caption">
        <pre class="sparql-error-pre">{{ error }}</pre>
      </div>
    </v-alert>

    <!-- ASK result (boolean) -->
    <v-alert
      v-else-if="results && !results.head.vars.length"
      :type="(results as any).boolean === true ? 'success' : 'warning'"
      variant="tonal"
    >
      ASK result: <strong>{{ (results as any).boolean }}</strong>
    </v-alert>

    <!-- SELECT results table -->
    <template v-else-if="results && results.head.vars.length">
      <div class="d-flex align-center justify-space-between mb-1">
        <span class="text-caption text-medium-emphasis">
          {{ rowCount }} row{{ rowCount !== 1 ? "s" : "" }} returned
        </span>
      </div>
      <v-card variant="outlined">
        <v-data-table
          :headers="tableHeaders"
          :items="tableItems"
          :items-per-page="25"
          density="compact"
          class="sparql-results-table"
        >
          <template
            v-for="h in tableHeaders"
            :key="h.key"
            #[`item.${h.key}`]="{ item }"
          >
            <span
              class="sparql-cell"
              :title="item[h.key] ?? ''"
            >{{ item[h.key] ?? "" }}</span>
          </template>
        </v-data-table>
      </v-card>
    </template>

  </div>
</template>

<style scoped>
.sparql-editor :deep(textarea) {
  font-family: "Fira Code", "JetBrains Mono", "Courier New", Courier, monospace !important;
  font-size: 0.875rem !important;
  line-height: 1.5 !important;
}

.sparql-results-table :deep(td),
.sparql-results-table :deep(th) {
  font-size: 0.8125rem;
}

.sparql-cell {
  display: inline-block;
  max-width: 360px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}

.sparql-error-pre {
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
}
</style>
