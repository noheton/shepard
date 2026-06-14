<script setup lang="ts">
// /shapes/validate — SHACL validation playground.
// Backed by POST /v2/shapes/validate (Jena SHACL).
// Two textareas (data graph + shape graph, both Turtle); a Run button;
// a structured violation report below.

import { extractShapeGraphFromTemplateBody } from "~/utils/shaclTemplateBody";
import { parseShaclReport, severityColor, severityIcon } from "~/utils/shaclValidateReport";
import { buildDataObjectRdfUrl, shouldFetchDataObjectRdf } from "~/utils/shaclPrefill";

useHead({ title: "SHACL playground | shepard" });

// TOOLS-CONTEXT-DO-SHACL — when navigated from a DataObject detail
// page the URL carries `?focusAppId=<doAppId>&scope=data-object`. When
// the DataObject has an attached template (TOOLS-CONTEXT-DO-TEMPLATE-DETECT-1),
// the in-context Tools menu also passes `?templateAppId=<...>`.
//
// SHAPES-V-PREFILL-1: on arrival with a templateAppId, fetch the
// template via GET /v2/templates/{appId} and show the resolved template
// name + body preview in the banner.
//
// SHAPES-V-PREFILL-2 (this commit): on arrival with a focusAppId and a
// non-collection scope, fetch the DataObject's Turtle subgraph from
// GET /v2/data-objects/{appId}/rdf and pre-fill the data-graph
// textarea. Validation is NEVER auto-run — the user clicks Validate.
//
// SHAPES-V-PREFILL-3-EXTRACT-SHACL (this commit): when the loaded
// template's body carries an optional `shapeGraph` Turtle string,
// extractShapeGraphFromTemplateBody() pulls it out and seeds the
// shape-graph textarea. The JSON DSL convention is documented on
// ShepardTemplateIO.body.
const route = useRoute();
const focusAppId = computed<string | null>(() =>
  typeof route.query.focusAppId === "string" ? route.query.focusAppId : null,
);
const focusScope = computed<string | null>(() =>
  typeof route.query.scope === "string" ? route.query.scope : null,
);
const templateAppId = computed<string | null>(() =>
  typeof route.query.templateAppId === "string" ? route.query.templateAppId : null,
);

interface TemplateBriefIO {
  appId?: string;
  name?: string;
  templateKind?: string;
  body?: string;
  description?: string | null;
}
const loadedTemplate = ref<TemplateBriefIO | null>(null);
const templateLoadError = ref<string | null>(null);
const isTemplateLoading = ref(false);

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
const result = ref<ReturnType<typeof parseShaclReport> | null>(null);
const error = ref<string | null>(null);
const isLoading = ref(false);

// SHAPES-V-PREFILL-2 — DataObject RDF auto-load state.
const rdfLoadError = ref<string | null>(null);
const isRdfLoading = ref(false);
const rdfPrefilled = ref(false);

function getV2Base(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  return explicit && explicit.length > 0
    ? explicit
    : (config.backendApiUrl as string).replace(/\/shepard\/api\/?$/, "");
}

async function fetchAttachedTemplate() {
  const id = templateAppId.value;
  if (!id) return;
  isTemplateLoading.value = true;
  templateLoadError.value = null;
  try {
    const { data: auth } = useAuth();
    const headers: Record<string, string> = { Accept: "application/json" };
    if (auth.value?.accessToken) headers["Authorization"] = `Bearer ${auth.value.accessToken}`;
    const res = await fetch(getV2Base() + `/v2/templates/${encodeURIComponent(id)}`, { headers });
    if (!res.ok) {
      templateLoadError.value = `${res.status} ${res.statusText}`;
      return;
    }
    loadedTemplate.value = await res.json();
    // SHAPES-V-PREFILL-1 — when the template body carries a
    // `shapeGraph` field (forward-compat — currently only added by
    // future SHACL-bearing templates), seed the shape graph textarea
    // with it. Otherwise leave the user's paste workflow intact.
    const extracted = extractShapeGraphFromTemplateBody(loadedTemplate.value?.body);
    if (extracted) {
      shapeGraph.value = extracted;
    }
  } catch (e) {
    templateLoadError.value = e instanceof Error ? e.message : String(e);
  } finally {
    isTemplateLoading.value = false;
  }
}

async function fetchDataObjectRdf() {
  if (!shouldFetchDataObjectRdf(focusAppId.value, focusScope.value)) return;
  const id = focusAppId.value;
  if (!id) return;
  isRdfLoading.value = true;
  rdfLoadError.value = null;
  try {
    const { data: auth } = useAuth();
    const headers: Record<string, string> = { Accept: "text/turtle" };
    if (auth.value?.accessToken) headers["Authorization"] = `Bearer ${auth.value.accessToken}`;
    const res = await fetch(buildDataObjectRdfUrl(getV2Base(), id), { headers });
    if (!res.ok) {
      rdfLoadError.value = `${res.status} ${res.statusText}`;
      return;
    }
    const turtle = await res.text();
    if (turtle && turtle.length > 0) {
      dataGraph.value = turtle;
      rdfPrefilled.value = true;
    }
    // Per the dispatch brief: do NOT auto-run validate() — the user
    // clicks the button after inspecting the prefilled content.
  } catch (e) {
    rdfLoadError.value = e instanceof Error ? e.message : String(e);
  } finally {
    isRdfLoading.value = false;
  }
}

onMounted(() => {
  void fetchAttachedTemplate();
  void fetchDataObjectRdf();
});

async function validate() {
  isLoading.value = true;
  error.value = null;
  result.value = null;
  try {
    const { data: auth } = useAuth();
    const headers: Record<string, string> = {
      "Content-Type": "application/json",
      Accept: "application/json",
    };
    if (auth.value?.accessToken) {
      headers["Authorization"] = `Bearer ${auth.value.accessToken}`;
    }
    const res = await fetch(getV2Base() + "/v2/shapes/validate", {
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
      result.value = parseShaclReport(JSON.parse(body));
    } catch {
      error.value = `Unexpected response: ${body}`;
    }
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : String(e);
  } finally {
    isLoading.value = false;
  }
}
</script>

<template>
  <!-- LAYOUT-4K-CENTERED-EMPTY-001 / L5: data-heavy tool page; fluid +
       2400px cap so the side-by-side data/shape graph editors and the
       validation report use the 4K canvas. -->
  <v-container fluid style="max-width: 2400px; margin: 0 auto">
    <div class="d-flex flex-column ga-2 mb-4">
      <h4 class="text-h4">SHACL validation playground</h4>
      <p class="text-body-1 text-medium-emphasis">
        Paste a Turtle data graph + a SHACL shape graph and get a validation
        report. Backed by Jena SHACL on
        <code>POST /v2/shapes/validate</code>.
      </p>
    </div>
    <v-alert
      v-if="focusAppId"
      type="info"
      variant="tonal"
      density="compact"
      class="mb-3"
      prepend-icon="mdi-tools"
      data-testid="shacl-focus-banner"
    >
      <div class="text-body-2">
        Validating against {{ focusScope === "collection" ? "Collection" : "DataObject" }}
        <code>{{ focusAppId }}</code>.
      </div>
      <div
        v-if="templateAppId"
        class="text-caption mt-1"
        data-testid="shacl-template-banner"
      >
        <template v-if="isTemplateLoading">
          Loading template <code>{{ templateAppId }}</code>…
        </template>
        <template v-else-if="templateLoadError">
          Failed to load template <code>{{ templateAppId }}</code>: {{ templateLoadError }}
        </template>
        <template v-else-if="loadedTemplate">
          Template attached: <strong>{{ loadedTemplate.name }}</strong>
          <span v-if="loadedTemplate.templateKind"> ({{ loadedTemplate.templateKind }})</span>.
          The shape graph below is prefilled if the template body declares one.
        </template>
      </div>
      <div
        v-if="focusScope !== 'collection'"
        class="text-caption mt-1"
        data-testid="shacl-rdf-banner"
      >
        <template v-if="isRdfLoading">
          Loading DataObject RDF…
        </template>
        <template v-else-if="rdfLoadError">
          Failed to load DataObject RDF: {{ rdfLoadError }}
        </template>
        <template v-else-if="rdfPrefilled">
          Data graph prefilled from
          <code>GET /v2/data-objects/{{ focusAppId }}/rdf</code>. Review and
          click Validate to run SHACL.
        </template>
      </div>
    </v-alert>

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
    <!-- Structured SHACL validation report (UI-GAP-6) -->
    <v-card v-if="result" variant="outlined" class="mt-3" data-testid="shacl-report">
      <v-card-title class="text-subtitle-1 d-flex align-center ga-2">
        Validation report
        <v-chip
          v-if="result.conforms"
          color="success"
          variant="tonal"
          size="small"
          prepend-icon="mdi-check-circle"
          data-testid="shacl-conforms-chip"
        >CONFORMS</v-chip>
        <v-chip
          v-else
          color="error"
          variant="tonal"
          size="small"
          prepend-icon="mdi-close-circle"
          data-testid="shacl-violations-chip"
        >{{ result.findings.length }} violation{{ result.findings.length === 1 ? "" : "s" }}</v-chip>
      </v-card-title>
      <v-card-text class="pa-0">
        <v-alert
          v-if="result.parseError"
          type="error"
          variant="tonal"
          density="compact"
          class="ma-3"
          data-testid="shacl-parse-error"
        >
          <strong>Parse error:</strong> {{ result.parseError }}
        </v-alert>
        <v-alert
          v-if="result.engineError"
          type="error"
          variant="tonal"
          density="compact"
          class="ma-3"
          data-testid="shacl-engine-error"
        >
          <strong>Engine error:</strong> {{ result.engineError }}
        </v-alert>
        <v-data-table
          v-if="result.findings.length > 0"
          :items="result.findings"
          :headers="[
            { title: 'Severity', key: 'severity', width: 110 },
            { title: 'Focus node', key: 'focusNode' },
            { title: 'Path', key: 'resultPath' },
            { title: 'Message', key: 'message' },
            { title: 'Value', key: 'value' },
          ]"
          density="compact"
          :items-per-page="25"
          data-testid="shacl-findings-table"
        >
          <template #item.severity="{ item }">
            <v-chip
              :color="severityColor(item.severity)"
              :prepend-icon="severityIcon(item.severity)"
              size="x-small"
              variant="tonal"
            >{{ item.severity }}</v-chip>
          </template>
          <template #item.focusNode="{ item }">
            <code class="text-caption">{{ item.focusNode ?? "—" }}</code>
          </template>
          <template #item.resultPath="{ item }">
            <code v-if="item.resultPath" class="text-caption">{{ item.resultPath }}</code>
            <span v-else class="text-medium-emphasis">—</span>
          </template>
          <template #item.value="{ item }">
            <code v-if="item.value" class="text-caption">{{ item.value }}</code>
            <span v-else class="text-medium-emphasis">—</span>
          </template>
        </v-data-table>
        <p
          v-else-if="result.conforms"
          class="text-body-2 text-medium-emphasis pa-4"
          data-testid="shacl-conforms-msg"
        >No violations — the data graph satisfies all shapes.</p>
      </v-card-text>
    </v-card>
  </v-container>
</template>

