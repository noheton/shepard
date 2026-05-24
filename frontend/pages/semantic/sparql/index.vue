<script setup lang="ts">
// /semantic/sparql — minimal SPARQL playground. Backed by N1f
// (GET/POST /v2/semantic/{repoAppId}/sparql). Read-only enforcement
// happens server-side via SparqlQueryValidator.
//
// First version: a textarea + Run button + JSON result dump. The
// repository selector is a free-text input until SemanticRepositoryPane
// exposes a "default repo" hint — most installs run a single internal
// repo (id often "internal").
//
// Design: aidocs/semantics/100 §5; backlog row N1f.

import PlaceholderImplStatus from "~/components/common/placeholder/PlaceholderImplStatus.vue";

useHead({ title: "SPARQL playground | shepard" });

const route = useRoute();
const repoId = ref<string>("internal");
const query = ref<string>(
  (route.query.query as string | undefined) ??
    "SELECT * WHERE { ?s ?p ?o } LIMIT 25",
);
const result = ref<unknown>(null);
const error = ref<string | null>(null);
const isLoading = ref(false);

async function runQuery() {
  isLoading.value = true;
  error.value = null;
  result.value = null;
  try {
    const { data: auth } = useAuth();
    const config = useRuntimeConfig().public;
    const explicit = config.backendV2ApiUrl as string | undefined;
    const v2Base =
      explicit && explicit.length > 0
        ? explicit
        : (config.backendApiUrl as string).replace(/\/shepard\/api\/?$/, "");
    const url =
      v2Base +
      `/v2/semantic/${encodeURIComponent(repoId.value)}/sparql?query=` +
      encodeURIComponent(query.value);
    const headers: Record<string, string> = {
      Accept: "application/sparql-results+json",
    };
    if (auth.value?.accessToken) {
      headers["Authorization"] = `Bearer ${auth.value.accessToken}`;
    }
    const res = await fetch(url, { headers });
    const body = await res.text();
    if (!res.ok) {
      error.value = `${res.status} ${res.statusText}\n${body}`;
      return;
    }
    try {
      result.value = JSON.parse(body);
    } catch {
      result.value = body;
    }
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : String(e);
  } finally {
    isLoading.value = false;
  }
}
</script>

<template>
  <v-container>
    <div class="d-flex flex-column ga-2 mb-4">
      <NuxtLink to="/semantic" class="text-caption">
        <v-icon size="small">mdi-arrow-left</v-icon> Semantic substrate
      </NuxtLink>
      <h4 class="text-h4">SPARQL playground</h4>
      <p class="text-body-1 text-medium-emphasis">
        Read-only SELECT / ASK queries against the internal n10s-managed
        graph. INSERT, DELETE, UPDATE, DROP, etc. are rejected server-side
        by <code>SparqlQueryValidator</code>.
      </p>
    </div>

    <v-text-field
      v-model="repoId"
      label="Repository ID"
      density="compact"
      variant="outlined"
      hint="Default is `internal` (the n10s-backed in-process store)."
      persistent-hint
      class="mb-3"
    />
    <v-textarea
      v-model="query"
      label="SPARQL query"
      variant="outlined"
      rows="6"
      auto-grow
      class="mb-2"
    />
    <v-btn color="primary" :loading="isLoading" @click="runQuery">
      <v-icon start>mdi-play</v-icon> Run query
    </v-btn>

    <v-alert v-if="error" type="error" class="mt-3" variant="tonal">
      <pre class="text-caption">{{ error }}</pre>
    </v-alert>
    <v-card v-if="result" variant="outlined" class="mt-3">
      <v-card-title class="text-subtitle-1">Result</v-card-title>
      <v-card-text>
        <pre class="text-caption sparql-result">{{ JSON.stringify(result, null, 2) }}</pre>
      </v-card-text>
    </v-card>

    <PlaceholderImplStatus
      backend="shipped"
      backlog-row="N1f"
      design-doc="aidocs/semantics/100-consistent-semantic-annotation-design.md"
      endpoint="/v2/semantic/{repoAppId}/sparql"
      notes="Backend live since N1f shipped. UI is intentionally minimal — full editor / autocomplete is queued."
    />
  </v-container>
</template>

<style scoped>
.sparql-result {
  max-height: 500px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
