<script setup lang="ts">
// /shapes/validate — minimal SHACL validation playground.
// Backed by POST /v2/shapes/validate (Jena SHACL).
// Two textareas (data graph + shape graph, both Turtle); a Run button;
// the validation report JSON below.

import PlaceholderImplStatus from "~/components/common/placeholder/PlaceholderImplStatus.vue";

useHead({ title: "SHACL playground | shepard" });

const dataGraph = ref<string>(
  `@prefix ex: <http://example.org/> .
ex:alice ex:age 30 .`,
);
const shapeGraph = ref<string>(
  `@prefix sh: <http://www.w3.org/ns/shacl#> .
@prefix ex: <http://example.org/> .

ex:PersonShape a sh:NodeShape ;
  sh:targetClass ex:Person ;
  sh:property [
    sh:path ex:age ;
    sh:minInclusive 0 ;
  ] .`,
);
const result = ref<unknown>(null);
const error = ref<string | null>(null);
const isLoading = ref(false);

async function validate() {
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
    const headers: Record<string, string> = {
      "Content-Type": "application/json",
      Accept: "application/json",
    };
    if (auth.value?.accessToken) {
      headers["Authorization"] = `Bearer ${auth.value.accessToken}`;
    }
    const res = await fetch(v2Base + "/v2/shapes/validate", {
      method: "POST",
      headers,
      body: JSON.stringify({
        dataGraph: dataGraph.value,
        shapeGraph: shapeGraph.value,
        dataGraphFormat: "TURTLE",
        shapeGraphFormat: "TURTLE",
      }),
    });
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
      <h4 class="text-h4">SHACL validation playground</h4>
      <p class="text-body-1 text-medium-emphasis">
        Paste a Turtle data graph + a SHACL shape graph and get a validation
        report. Backed by Jena SHACL on
        <code>POST /v2/shapes/validate</code>.
      </p>
    </div>
    <v-row>
      <v-col cols="12" md="6">
        <v-textarea
          v-model="dataGraph"
          label="Data graph (Turtle)"
          variant="outlined"
          rows="10"
          auto-grow
        />
      </v-col>
      <v-col cols="12" md="6">
        <v-textarea
          v-model="shapeGraph"
          label="Shape graph (Turtle)"
          variant="outlined"
          rows="10"
          auto-grow
        />
      </v-col>
    </v-row>
    <v-btn color="primary" :loading="isLoading" @click="validate">
      <v-icon start>mdi-play</v-icon> Validate
    </v-btn>
    <v-alert v-if="error" type="error" class="mt-3" variant="tonal">
      <pre class="text-caption">{{ error }}</pre>
    </v-alert>
    <v-card v-if="result" variant="outlined" class="mt-3">
      <v-card-title class="text-subtitle-1">Validation report</v-card-title>
      <v-card-text>
        <pre class="text-caption shacl-result">{{ JSON.stringify(result, null, 2) }}</pre>
      </v-card-text>
    </v-card>
    <PlaceholderImplStatus
      backend="shipped"
      backlog-row="SHAPES-V"
      design-doc="aidocs/semantics/100-consistent-semantic-annotation-design.md"
      endpoint="/v2/shapes/validate"
      notes="Backend live. UI is intentionally minimal — full editor / SHACL-shape library queued under SHAPES-V-UI."
    />
  </v-container>
</template>

<style scoped>
.shacl-result {
  max-height: 500px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
