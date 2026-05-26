<script setup lang="ts">
// /semantic/sparql — SPARQL playground for all authenticated users.
// Backed by N1f (POST /v2/semantic/{repoAppId}/sparql).
// Read-only enforcement happens server-side via SparqlQueryValidator.
//
// Design: aidocs/semantics/100 §5; backlog row N1f.
// Admin version at /admin#sparql-playground uses the same composable.

import type { SparqlBinding } from "~/composables/context/admin/useSparqlPlayground";
import { useSparqlPlayground } from "~/composables/context/admin/useSparqlPlayground";
import PlaceholderImplStatus from "~/components/common/placeholder/PlaceholderImplStatus.vue";

useHead({ title: "SPARQL playground | shepard" });

const route = useRoute();

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

// Seed query from URL param if provided (?query=...)
if (route.query.query) {
  query.value = route.query.query as string;
}

// ─── Table rendering ───────────────────────────────────────────────────────

const tableHeaders = computed(() => {
  if (!results.value || !results.value.head.vars.length) return [];
  return results.value.head.vars.map((v: string) => ({
    title: `?${v}`,
    key: v,
    sortable: true,
  }));
});

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

// Toggle between table view and raw JSON
const showRaw = ref(false);
</script>

<template>
  <v-container>
    <div class="d-flex flex-column ga-2 mb-4">
      <NuxtLink to="/semantic" class="text-caption">
        <v-icon size="small">mdi-arrow-left</v-icon> Semantic substrate
      </NuxtLink>
      <h4 class="text-h4">SPARQL playground</h4>
      <p class="text-body-1 text-medium-emphasis">
        Read-only <code>SELECT</code> / <code>ASK</code> queries against the
        internal n10s-managed graph. Mutation forms are rejected server-side.
        Admin version available at
        <NuxtLink to="/admin#sparql-playground">/admin#sparql-playground</NuxtLink>.
      </p>
    </div>

    <v-text-field
      v-model="repoId"
      label="Repository ID (appId)"
      density="compact"
      variant="outlined"
      hint="Default is `internal` (the n10s-backed in-process store)."
      persistent-hint
      class="mb-3"
      style="max-width: 440px"
    />

    <v-textarea
      v-model="query"
      label="SPARQL query"
      variant="outlined"
      :rows="8"
      auto-grow
      class="mb-1 sparql-editor"
      @keydown.ctrl.enter.prevent="runQuery"
    />
    <div class="text-caption text-medium-emphasis mb-3">
      Tip: press <kbd>Ctrl</kbd>+<kbd>Enter</kbd> to run.
    </div>

    <div class="d-flex ga-2 mb-3">
      <v-btn color="primary" :loading="isLoading" prepend-icon="mdi-play" @click="runQuery">
        Run query
      </v-btn>
      <v-btn variant="text" :disabled="isLoading" prepend-icon="mdi-refresh" @click="resetQuery">
        Reset
      </v-btn>
    </div>

    <!-- Error -->
    <v-alert v-if="error" type="error" class="mt-3" variant="tonal" closable @click:close="error = null">
      <pre class="text-caption sparql-error-pre">{{ error }}</pre>
    </v-alert>

    <!-- ASK result (boolean) -->
    <v-alert
      v-else-if="results && results.head && !results.head.vars.length"
      :type="(results as any).boolean === true ? 'success' : 'warning'"
      variant="tonal"
      class="mt-3"
    >
      ASK result: <strong>{{ (results as any).boolean }}</strong>
    </v-alert>

    <!-- SELECT results -->
    <template v-else-if="results && results.head && results.head.vars.length">
      <div class="d-flex align-center ga-3 mt-3 mb-1">
        <span class="text-caption text-medium-emphasis">
          {{ rowCount }} row{{ rowCount !== 1 ? "s" : "" }} returned
        </span>
        <v-btn-toggle v-model="showRaw" density="compact" variant="outlined" size="small">
          <v-btn :value="false" size="small">Table</v-btn>
          <v-btn :value="true" size="small">JSON</v-btn>
        </v-btn-toggle>
      </div>

      <!-- Table view -->
      <v-card v-if="!showRaw" variant="outlined">
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
            <span class="sparql-cell" :title="item[h.key] ?? ''">{{ item[h.key] ?? "" }}</span>
          </template>
        </v-data-table>
      </v-card>

      <!-- Raw JSON view -->
      <v-card v-else variant="outlined">
        <v-card-text>
          <pre class="text-caption sparql-result">{{ JSON.stringify(results, null, 2) }}</pre>
        </v-card-text>
      </v-card>
    </template>

    <PlaceholderImplStatus
      backend="shipped"
      backlog-row="N1f"
      design-doc="aidocs/semantics/100-consistent-semantic-annotation-design.md"
      endpoint="/v2/semantic/{repoAppId}/sparql"
      notes="Backend live since N1f shipped. Table rendering shipped in #64. Full editor / autocomplete queued."
    />
  </v-container>
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

.sparql-result {
  max-height: 500px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
}

.sparql-error-pre {
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
}
</style>
