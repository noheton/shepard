<script setup lang="ts">
/**
 * V2CONV-B6 — the visual template editor.
 *
 * Lets an admin / power-user compose a `ShepardTemplate` (a SHACL shape) by
 * picking semantic predicates from the vocabulary palette, without hand-writing
 * Turtle. Four parts, left-to-right:
 *
 *   1. PALETTE — search + curated predicates (`/v2/shapes/predicates` +
 *      `/v2/semantic/terms/search`). Click a predicate → adds a property row.
 *   2. COMPOSE — property-shape rows: path (predicate IRI), datatype, min/max
 *      count, optional `sh:in` value set, optional nested `sh:node`. Plus the
 *      node-shape's IRI + target class + closed flag.
 *   3. PREVIEW — serialises the rows to the B1 JSON DSL and shows the compiled
 *      `shapeGraph` Turtle (debounced `POST /v2/shapes/build`).
 *   4. VALIDATE — round-trips a candidate data graph against the compiled shape
 *      (`POST /v2/shapes/validate`).
 *
 * Emits `update:body` with the persisted template body (editorState + compiled
 * shapeGraph) so the parent dialog can save it via the existing template CRUD.
 *
 * Design: aidocs/platform/191-v2-surface-convergence.md §3.
 */
import { ref, computed, watch, onMounted } from "vue";
import {
  useShapePalette,
  filterPalette,
  mergePaletteSources,
  type PalettePredicate,
} from "~/composables/semantic/useShapePalette";
import { useShapeBuilder } from "~/composables/semantic/useShapeBuilder";
import {
  emptyShapeEditorState,
  propertyRowFromPalette,
  editorStateToBuildRequest,
  buildTemplateBody,
  editorStateFromTemplateBody,
  DATATYPE_OPTIONS,
  type ShapeEditorState,
  type PropertyRow,
} from "~/utils/templateShapeDsl";

const props = defineProps<{
  /** The current template body JSON (reopened into editor state if it carries one). */
  body?: string | null;
}>();

const emit = defineEmits<{
  /** Emitted with the new template body whenever the editor produces a valid compile. */
  (e: "update:body", body: string): void;
}>();

// ─── editor state ──────────────────────────────────────────────────────────
const state = ref<ShapeEditorState>(emptyShapeEditorState());

// ─── palette ───────────────────────────────────────────────────────────────
const { vocabulary, searchResults, loadingVocabulary, searching, loadVocabulary, search } =
  useShapePalette();
const paletteQuery = ref("");

const combinedPalette = computed<PalettePredicate[]>(() =>
  mergePaletteSources(filterPalette(vocabulary.value, paletteQuery.value), searchResults.value),
);

let searchTimer: ReturnType<typeof setTimeout> | null = null;
watch(paletteQuery, (q) => {
  if (searchTimer) clearTimeout(searchTimer);
  searchTimer = setTimeout(() => void search(q), 300);
});

function addPredicate(p: PalettePredicate) {
  state.value.properties.push(
    propertyRowFromPalette({ uri: p.uri, label: p.label, datatype: p.datatype, cardinality: p.cardinality }),
  );
}

function addBlankRow() {
  state.value.properties.push({ path: "", datatype: "", minCount: null, maxCount: null, in: [], node: "" });
}

function removeRow(idx: number) {
  state.value.properties.splice(idx, 1);
}

function addInMember(row: PropertyRow) {
  if (!row.in) row.in = [];
  row.in.push({ value: "", kind: "LITERAL", datatype: null });
}

function removeInMember(row: PropertyRow, idx: number) {
  row.in?.splice(idx, 1);
}

// ─── live preview (compile) ──────────────────────────────────────────────────
const { compiledTurtle, compiledShapeIri, compileError, compiling, report, validateError, validating, compile, validate } =
  useShapeBuilder();

const hasRows = computed(() => state.value.properties.some((p) => (p.path ?? "").trim().length > 0));

// Track the body we last emitted so the props.body watch can ignore the
// echo of our own emit (otherwise reopening it would re-trigger the deep
// state watch → recompile → emit → … feedback loop).
let lastEmittedBody: string | null = null;

let compileTimer: ReturnType<typeof setTimeout> | null = null;
async function recompile() {
  const dsl = editorStateToBuildRequest(state.value);
  const result = await compile(dsl);
  if (result.shapeGraph) {
    const newBody = buildTemplateBody(state.value, result.shapeGraph, props.body);
    lastEmittedBody = newBody;
    emit("update:body", newBody);
  }
}

// Debounced recompile whenever the editor state changes.
watch(
  state,
  () => {
    if (compileTimer) clearTimeout(compileTimer);
    compileTimer = setTimeout(() => void recompile(), 400);
  },
  { deep: true },
);

// ─── validate round-trip ─────────────────────────────────────────────────────
const dataGraph = ref<string>(`@prefix ex: <http://example.org/> .
ex:sample a ex:Thing .`);

async function runValidate() {
  if (!compiledTurtle.value) await recompile();
  if (compiledTurtle.value) await validate(dataGraph.value, compiledTurtle.value);
}

// ─── lifecycle ───────────────────────────────────────────────────────────────
onMounted(async () => {
  await loadVocabulary();
  const reopened = editorStateFromTemplateBody(props.body);
  if (reopened) {
    state.value = reopened;
    await recompile();
  }
});

// Reopen when the body prop changes from the OUTSIDE (e.g. switching
// templates in the dialog) — but ignore the echo of our own emit.
watch(
  () => props.body,
  (b) => {
    if (b === lastEmittedBody) return;
    const reopened = editorStateFromTemplateBody(b);
    if (reopened) state.value = reopened;
  },
);

defineExpose({ recompile, state });
</script>

<template>
  <div class="d-flex flex-column ga-3" data-test="template-shape-editor">
    <v-row dense>
      <!-- ── PALETTE ──────────────────────────────────────────────── -->
      <v-col cols="12" md="4">
        <v-card variant="outlined" class="pa-3 d-flex flex-column ga-2" style="height: 100%">
          <div class="text-subtitle-2 d-flex align-center">
            <v-icon icon="mdi-palette-outline" size="small" class="mr-2" />
            Predicate palette
          </div>
          <v-text-field
            v-model="paletteQuery"
            label="Search predicates / ontology terms"
            density="compact"
            variant="outlined"
            clearable
            hide-details
            prepend-inner-icon="mdi-magnify"
            :loading="loadingVocabulary || searching"
            data-test="palette-search"
          />
          <div class="palette-list">
            <centered-loading-spinner v-if="loadingVocabulary && vocabulary.length === 0" />
            <v-list v-else density="compact" lines="two">
              <v-list-item
                v-for="p in combinedPalette"
                :key="p.uri"
                :title="p.label || p.uri"
                :subtitle="p.uri"
                data-test="palette-item"
                @click="addPredicate(p)"
              >
                <template #append>
                  <v-chip
                    size="x-small"
                    :color="p.source === 'vocabulary' ? 'primary' : 'secondary'"
                    variant="tonal"
                  >
                    {{ p.source === "vocabulary" ? "vocab" : "search" }}
                  </v-chip>
                  <v-icon icon="mdi-plus" size="small" class="ml-2" />
                </template>
              </v-list-item>
              <v-list-item
                v-if="combinedPalette.length === 0"
                title="No predicates found"
                subtitle="Type to search the ontology, or add a blank row."
              />
            </v-list>
          </div>
          <v-btn size="small" variant="tonal" prepend-icon="mdi-plus" data-test="add-blank-row" @click="addBlankRow">
            Add blank property
          </v-btn>
        </v-card>
      </v-col>

      <!-- ── COMPOSE ──────────────────────────────────────────────── -->
      <v-col cols="12" md="8">
        <v-card variant="outlined" class="pa-3 d-flex flex-column ga-2">
          <div class="text-subtitle-2 d-flex align-center">
            <v-icon icon="mdi-shape-outline" size="small" class="mr-2" />
            Compose shape
          </div>

          <v-row dense>
            <v-col cols="12" sm="6">
              <v-text-field
                v-model="state.shapeIri"
                label="Shape IRI (optional)"
                placeholder="urn:shepard:shape:my-recipe"
                density="compact"
                variant="outlined"
                hide-details
                data-test="shape-iri"
              />
            </v-col>
            <v-col cols="12" sm="6">
              <v-text-field
                v-model="state.targetClass"
                label="Target class IRI (optional)"
                placeholder="http://semantics.dlr.de/shepard#DataObject"
                density="compact"
                variant="outlined"
                hide-details
                data-test="target-class"
              />
            </v-col>
            <v-col cols="12">
              <v-switch
                v-model="state.closed"
                label="Closed shape (sh:closed — reject undeclared predicates)"
                color="primary"
                density="compact"
                hide-details
                data-test="closed-switch"
              />
            </v-col>
          </v-row>

          <v-divider />

          <div v-if="state.properties.length === 0" class="text-medium-emphasis text-body-2 py-4 text-center">
            Pick a predicate from the palette (or add a blank property) to start composing.
          </div>

          <v-card
            v-for="(row, idx) in state.properties"
            :key="idx"
            variant="tonal"
            class="pa-3 mb-1"
            data-test="property-row"
          >
            <div class="d-flex align-center mb-2">
              <v-icon icon="mdi-link-variant" size="small" class="mr-2" />
              <span class="text-caption font-weight-medium">{{ row.label || "Property " + (idx + 1) }}</span>
              <v-spacer />
              <v-btn
                icon="mdi-delete-outline"
                size="x-small"
                variant="text"
                color="error"
                data-test="remove-row"
                @click="removeRow(idx)"
              />
            </div>
            <v-row dense>
              <v-col cols="12">
                <v-text-field
                  v-model="row.path"
                  label="Predicate IRI (sh:path)"
                  density="compact"
                  variant="outlined"
                  hide-details
                  :error="!row.path?.trim()"
                  data-test="row-path"
                />
              </v-col>
              <v-col cols="12" sm="5">
                <v-select
                  v-model="row.datatype"
                  :items="DATATYPE_OPTIONS"
                  item-title="title"
                  item-value="value"
                  label="Datatype (sh:datatype)"
                  density="compact"
                  variant="outlined"
                  hide-details
                  data-test="row-datatype"
                />
              </v-col>
              <v-col cols="6" sm="3">
                <v-text-field
                  v-model.number="row.minCount"
                  label="minCount"
                  type="number"
                  min="0"
                  density="compact"
                  variant="outlined"
                  hide-details
                  clearable
                  data-test="row-min"
                />
              </v-col>
              <v-col cols="6" sm="4">
                <v-text-field
                  v-model.number="row.maxCount"
                  label="maxCount"
                  type="number"
                  min="0"
                  density="compact"
                  variant="outlined"
                  hide-details
                  clearable
                  data-test="row-max"
                />
              </v-col>
              <v-col cols="12">
                <v-text-field
                  v-model="row.node"
                  label="Nested node shape IRI (sh:node, optional)"
                  density="compact"
                  variant="outlined"
                  hide-details
                  data-test="row-node"
                />
              </v-col>
            </v-row>

            <!-- sh:in value set -->
            <div class="mt-2">
              <div class="d-flex align-center mb-1">
                <span class="text-caption">Allowed values (sh:in, optional)</span>
                <v-btn
                  icon="mdi-plus"
                  size="x-small"
                  variant="text"
                  data-test="add-in-member"
                  @click="addInMember(row)"
                />
              </div>
              <div v-for="(m, mIdx) in row.in" :key="mIdx" class="d-flex align-center ga-1 mb-1">
                <v-text-field
                  v-model="m.value"
                  label="Value"
                  density="compact"
                  variant="outlined"
                  hide-details
                  data-test="in-value"
                />
                <v-select
                  v-model="m.kind"
                  :items="['LITERAL', 'IRI']"
                  label="Kind"
                  density="compact"
                  variant="outlined"
                  hide-details
                  style="max-width: 120px"
                  data-test="in-kind"
                />
                <v-btn
                  icon="mdi-close"
                  size="x-small"
                  variant="text"
                  color="error"
                  @click="removeInMember(row, mIdx)"
                />
              </div>
            </div>
          </v-card>
        </v-card>
      </v-col>
    </v-row>

    <!-- ── PREVIEW + VALIDATE ───────────────────────────────────────── -->
    <v-row dense>
      <v-col cols="12" md="6">
        <v-card variant="outlined" class="pa-3" data-test="shape-preview">
          <div class="text-subtitle-2 d-flex align-center mb-2">
            <v-icon icon="mdi-code-braces" size="small" class="mr-2" />
            Live SHACL preview
            <v-progress-circular v-if="compiling" indeterminate size="16" width="2" class="ml-2" />
            <v-spacer />
            <span v-if="compiledShapeIri" class="text-caption text-medium-emphasis">{{ compiledShapeIri }}</span>
          </div>
          <v-alert v-if="compileError" type="warning" density="compact" variant="tonal" class="mb-2">
            {{ compileError }}
          </v-alert>
          <pre v-if="compiledTurtle" class="ttl-preview text-caption" data-test="ttl-preview">{{ compiledTurtle }}</pre>
          <div v-else-if="!hasRows" class="text-medium-emphasis text-body-2">
            Compose at least one property to see the compiled SHACL.
          </div>
        </v-card>
      </v-col>

      <v-col cols="12" md="6">
        <v-card variant="outlined" class="pa-3" data-test="shape-validate">
          <div class="text-subtitle-2 d-flex align-center mb-2">
            <v-icon icon="mdi-check-decagram-outline" size="small" class="mr-2" />
            Round-trip validate
          </div>
          <v-textarea
            v-model="dataGraph"
            label="Candidate data graph (Turtle)"
            rows="5"
            density="compact"
            variant="outlined"
            hide-details
            class="mb-2"
            data-test="validate-data-graph"
          />
          <v-btn
            size="small"
            color="primary"
            variant="tonal"
            :loading="validating"
            :disabled="!compiledTurtle"
            prepend-icon="mdi-play"
            data-test="validate-run"
            @click="runValidate"
          >
            Validate against this shape
          </v-btn>
          <v-alert v-if="validateError" type="error" density="compact" variant="tonal" class="mt-2">
            <pre class="text-caption">{{ validateError }}</pre>
          </v-alert>
          <v-alert
            v-else-if="report"
            :type="report.conforms ? 'success' : 'warning'"
            density="compact"
            variant="tonal"
            class="mt-2"
            data-test="validate-report"
          >
            <div v-if="report.parseError">Parse error: {{ report.parseError }}</div>
            <div v-else-if="report.conforms">Conforms — the data satisfies this shape.</div>
            <div v-else>
              {{ report.findings.length }} violation(s):
              <ul class="text-caption">
                <li v-for="(f, i) in report.findings" :key="i">
                  <code>{{ f.resultPath || f.focusNode }}</code> — {{ f.message || f.severity }}
                </li>
              </ul>
            </div>
          </v-alert>
        </v-card>
      </v-col>
    </v-row>
  </div>
</template>

<style scoped lang="scss">
.palette-list {
  max-height: 360px;
  overflow-y: auto;
}
.ttl-preview {
  max-height: 340px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  background: rgba(var(--v-theme-on-surface), 0.04);
  padding: 8px;
  border-radius: 4px;
}
</style>
