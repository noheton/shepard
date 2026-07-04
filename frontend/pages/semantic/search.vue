<script setup lang="ts">
/**
 * /semantic/search — L4 — ontology search-as-you-type with tree / graph view.
 *
 * A researcher types into the search box and ontology terms filter live
 * (300 ms debounce) against `GET /v2/semantic/terms/search`. Matched terms are
 * grouped by their IRI namespace into a hierarchy and shown two ways:
 *
 *   - Tree view  (default) — a `v-list` of namespace groups, each expandable to
 *     its matched terms. Lightweight, fully keyboard-accessible, no extra lib.
 *   - Graph view (toggle)  — the matched namespace → term hierarchy rendered as
 *     an ECharts + @dagrejs/dagre layered graph, reusing the same engine the
 *     collection-lineage and prov graphs already use.
 *
 * The search response is a flat `{ uri, label, description }` list with no
 * explicit broader/narrower edges, so the hierarchy is derived from the IRI
 * namespace (the standard ontology-browser fallback — see
 * `utils/ontologyTermTree.ts`). If the backend later exposes a real
 * broader/narrower axis, the tree builder is the one place to extend.
 *
 * Frontend-v2-only: the composable targets `/v2/` and addresses terms by IRI;
 * no numeric ids, no `useShepardApi`. Each matched term deep-links into the
 * predicate detail page and the SPARQL playground.
 *
 * Design: aidocs/16 row L4; intersects aidocs/semantics/13-search-improvements.md.
 */

import VChart from "vue-echarts";
import { use } from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import { GraphChart } from "echarts/charts";
import { TooltipComponent, LegendComponent } from "echarts/components";
import dagre from "@dagrejs/dagre";
import { useOntologySearch, MIN_QUERY_LENGTH } from "~/composables/semantic/useOntologySearch";
import { baseGraphSeriesConfig } from "~/composables/useLineageGraph";
import type { TermTreeLeaf } from "~/utils/ontologyTermTree";

if (import.meta.client) {
  use([CanvasRenderer, GraphChart, TooltipComponent, LegendComponent]);
}

useHead({ title: "Ontology search | semantic | shepard" });

const { query, loading, error, searched, tree, graph, total } = useOntologySearch();

type ViewMode = "tree" | "graph";
const view = ref<ViewMode>("tree");

// Open all namespace groups whose ids are present; default-open everything so a
// match is never hidden one click deep.
const openGroups = ref<string[]>([]);
watch(tree, (next) => {
  openGroups.value = next.map((g) => g.id);
});

function predicateRoute(leaf: TermTreeLeaf) {
  return `/semantic/predicates/${encodeURIComponent(leaf.uri)}`;
}
function sparqlRoute(leaf: TermTreeLeaf) {
  const q =
    `SELECT ?s ?p ?o WHERE {\n  ?s ?p <${leaf.uri}> .\n} LIMIT 50`;
  return `/semantic/sparql?query=${encodeURIComponent(q)}`;
}

// ─── graph view (ECharts + dagre) ──────────────────────────────────────────────

const NAMESPACE_COLOR = "#5B8FF9";
const TERM_COLOR = "#61DDAA";

const chartOption = computed(() => {
  const { nodes, edges } = graph.value;
  if (nodes.length === 0) return {};

  // Layered layout: namespaces on the left rank, their terms to the right.
  const g = new dagre.graphlib.Graph();
  g.setGraph({ rankdir: "LR", nodesep: 28, ranksep: 220, marginx: 40, marginy: 40 });
  g.setDefaultEdgeLabel(() => ({}));
  for (const n of nodes) {
    g.setNode(n.id, { width: 120, height: 30 });
  }
  for (const e of edges) {
    g.setEdge(e.source, e.target);
  }
  dagre.layout(g);

  const echartNodes = nodes.map((n) => {
    const pos = g.node(n.id);
    return {
      id: n.id,
      name: n.name,
      x: pos?.x ?? 0,
      y: pos?.y ?? 0,
      symbolSize: n.symbolSize,
      category: n.category,
      itemStyle: { color: n.category === 0 ? NAMESPACE_COLOR : TERM_COLOR },
      tooltip: { formatter: n.uri },
      label: { show: true, formatter: n.name },
    };
  });
  const echartEdges = edges.map((e) => ({ source: e.source, target: e.target }));

  return {
    tooltip: { confine: true },
    legend: [{ data: ["Namespace", "Term"], bottom: 0 }],
    series: [
      {
        ...baseGraphSeriesConfig(),
        layout: "none",
        categories: [{ name: "Namespace" }, { name: "Term" }],
        data: echartNodes,
        links: echartEdges,
        label: { show: true, position: "right", color: "inherit" },
      },
    ],
  };
});
</script>

<template>
  <v-container>
    <div class="d-flex flex-column ga-2 mb-4">
      <NuxtLink to="/me#semantic" class="text-caption">
        <v-icon size="small">mdi-arrow-left</v-icon> Semantic substrate
      </NuxtLink>
      <h4 class="text-h4">Ontology search</h4>
      <p class="text-body-1 text-medium-emphasis">
        Type to search every ontology term loaded into the internal semantic
        store. Results filter live and are grouped by their vocabulary
        namespace so you can see where a matched term sits. Switch to the graph
        view to see the same matches as a namespace → term hierarchy.
      </p>
    </div>

    <v-text-field
      v-model="query"
      data-testid="ontology-search-input"
      density="comfortable"
      variant="outlined"
      clearable
      autofocus
      hide-details
      placeholder="Search ontology terms — e.g. creator, anomaly, sensor"
      prepend-inner-icon="mdi-magnify"
      role="combobox"
      :aria-expanded="tree.length > 0"
      aria-label="Search ontology terms"
      class="mb-3"
    >
      <template #append-inner>
        <v-progress-circular
          v-if="loading"
          indeterminate
          size="20"
          width="2"
          color="primary"
        />
      </template>
    </v-text-field>

    <div class="d-flex align-center justify-space-between mb-3 flex-wrap ga-2">
      <div class="text-caption text-medium-emphasis" data-testid="ontology-search-count">
        <template v-if="searched && !loading">
          {{ total }} term{{ total === 1 ? "" : "s" }} matched
          across {{ tree.length }} namespace{{ tree.length === 1 ? "" : "s" }}
        </template>
        <template v-else-if="query.trim().length > 0 && query.trim().length < MIN_QUERY_LENGTH">
          Keep typing — at least {{ MIN_QUERY_LENGTH }} characters
        </template>
      </div>
      <v-btn-toggle
        v-model="view"
        mandatory
        density="compact"
        variant="outlined"
        color="primary"
        data-testid="ontology-search-viewtoggle"
      >
        <v-btn value="tree" size="small" prepend-icon="mdi-file-tree">Tree</v-btn>
        <v-btn value="graph" size="small" prepend-icon="mdi-graph-outline">Graph</v-btn>
      </v-btn-toggle>
    </div>

    <v-alert
      v-if="error && !loading"
      type="error"
      variant="tonal"
      class="mb-3"
      data-testid="ontology-search-error"
    >
      Term search failed: {{ error }}
    </v-alert>

    <!-- Empty / prompt states -->
    <v-alert
      v-if="!searched && !loading && !error"
      type="info"
      variant="tonal"
      data-testid="ontology-search-prompt"
      text="Start typing above to search the ontology. Matches appear as you type."
    />
    <v-alert
      v-else-if="searched && !loading && !error && total === 0"
      type="info"
      variant="tonal"
      data-testid="ontology-search-empty"
    >
      No ontology terms match “{{ query }}”. Try a shorter or different term, or
      <NuxtLink to="/semantic/vocabularies">browse the full vocabulary list</NuxtLink>.
      If no ontologies are loaded at all, an instance-admin can upload bundles
      via the Semantic Repositories admin pane.
    </v-alert>

    <!-- Tree view -->
    <v-card
      v-else-if="view === 'tree' && total > 0"
      variant="outlined"
      data-testid="ontology-search-tree"
    >
      <v-list v-model:opened="openGroups" open-strategy="multiple" density="comfortable">
        <v-list-group
          v-for="group in tree"
          :key="group.id"
          :value="group.id"
        >
          <template #activator="{ props: activatorProps }">
            <v-list-item
              v-bind="activatorProps"
              :title="group.shortPrefix"
              :subtitle="group.namespace"
            >
              <template #prepend>
                <v-icon>mdi-bookshelf</v-icon>
              </template>
              <template #append>
                <v-chip size="x-small" variant="tonal">{{ group.children.length }}</v-chip>
              </template>
            </v-list-item>
          </template>

          <v-list-item
            v-for="leaf in group.children"
            :key="leaf.id"
            :title="leaf.label"
            class="pl-8"
            :data-testid="`ontology-term-${leaf.localName}`"
          >
            <template #prepend>
              <v-icon size="small">mdi-tag-outline</v-icon>
            </template>
            <v-list-item-subtitle>
              <code class="text-caption text-break">{{ leaf.uri }}</code>
              <div v-if="leaf.description" class="text-caption text-medium-emphasis mt-1">
                {{ leaf.description }}
              </div>
            </v-list-item-subtitle>
            <template #append>
              <v-btn
                :to="predicateRoute(leaf)"
                size="x-small"
                variant="text"
                icon="mdi-information-outline"
                :aria-label="`Details for ${leaf.label}`"
              />
              <v-btn
                :to="sparqlRoute(leaf)"
                size="x-small"
                variant="text"
                icon="mdi-code-braces"
                :aria-label="`Query ${leaf.label} in SPARQL`"
              />
            </template>
          </v-list-item>
        </v-list-group>
      </v-list>
    </v-card>

    <!-- Graph view -->
    <v-card
      v-else-if="view === 'graph' && total > 0"
      variant="outlined"
      data-testid="ontology-search-graph"
    >
      <client-only>
        <VChart
          :option="chartOption"
          autoresize
          style="width: 100%; height: 70vh; min-height: 420px"
        />
        <template #fallback>
          <div class="d-flex align-center justify-center" style="height: 420px">
            <v-progress-circular indeterminate color="primary" />
          </div>
        </template>
      </client-only>
    </v-card>
  </v-container>
</template>
