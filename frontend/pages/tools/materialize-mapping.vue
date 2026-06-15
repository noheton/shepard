<script setup lang="ts">
/**
 * /tools/materialize-mapping — V2CONV-B3-FE MAPPING_RECIPE materializer.
 *
 * UI-GAP-1 slice 1: replaced bare templateAppId text-field with
 * TemplateAutocomplete scoped to MAPPING_RECIPE kind. Route query param
 * `templateAppId` pre-populates from the in-context "Materialize" action
 * on DataObject detail pages (do-materialize toolsContext entry).
 *
 * Per the CLAUDE.md rules this surface addresses entities by appId only,
 * targets /v2/ exclusively, and never asks the user for a path or URL.
 *
 * Design: aidocs/platform/191 §4. Backlog: V2CONV-B3, UI-GAP-1.
 */

import {
  materializeMapping,
  type MaterializeResponse,
} from "~/composables/useMaterializeMapping";

useHead({ title: "Materialize mapping | shepard" });

const route = useRoute();

interface BindingRow {
  role: string;
  appId: string;
}

// Pre-populate templateAppId from in-context route query (do-materialize entry).
const templateAppId = ref<string>(
  typeof route.query.templateAppId === "string" ? route.query.templateAppId : "",
);
const bindings = ref<BindingRow[]>([{ role: "srcFileAppId", appId: "" }]);
const submitting = ref<boolean>(false);
const result = ref<MaterializeResponse | null>(null);
const error = ref<string | null>(null);

function addBinding() {
  bindings.value.push({ role: "", appId: "" });
}

function removeBinding(idx: number) {
  bindings.value.splice(idx, 1);
}

async function submit() {
  if (!templateAppId.value.trim()) {
    error.value = "Enter the MAPPING_RECIPE template appId first.";
    return;
  }
  submitting.value = true;
  error.value = null;
  result.value = null;
  try {
    const inputMap: Record<string, string> = {};
    for (const row of bindings.value) {
      if (row.role.trim()) inputMap[row.role.trim()] = row.appId;
    }
    result.value = await materializeMapping(templateAppId.value.trim(), inputMap);
  } catch (e: unknown) {
    const err = e as { data?: { error?: string }; message?: string };
    error.value =
      err.data?.error ?? err.message ?? "Request failed — see browser console.";
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <v-container class="py-6">
    <PlaceholderPageHeader
      title="Materialize a mapping recipe"
      subtitle="Bind existing reference appIds through a MAPPING_RECIPE template and derive an output — a new reference, or a played/rendered view. The generic enabler behind scene-graph and KRL (V2CONV-B4/B5)."
      design-doc-href="https://github.com/nucli-de/shepard/blob/main/aidocs/platform/191-v2-surface-convergence.md"
      design-doc-label="aidocs/platform/191 §4"
    />

    <v-alert type="info" variant="tonal" class="mb-6" icon="mdi-information-outline">
      <div class="text-body-2">
        <p class="mb-2">
          This page POSTs to
          <code>POST /v2/mappings/{templateAppId}/materialize</code>. The backend
          resolves the recipe's <code>mappingRecipeShape</code> IRI to a
          registered <code>TransformExecutor</code> and runs it against the input
          reference appIds you bind below.
        </p>
        <p class="mb-0">
          The built-in identity executor echoes the first input reference appId
          back as the derived reference — so this flow is exercisable end-to-end
          before any scene-graph / KRL plugin is installed.
        </p>
      </div>
    </v-alert>

    <v-card class="mb-6">
      <v-card-title>Inputs</v-card-title>
      <v-card-text>
        <!-- UI-GAP-1: TemplateAutocomplete replaces the bare appId text field. -->
        <TemplateAutocomplete
          kind="MAPPING_RECIPE"
          label="MAPPING_RECIPE template"
          v-model:appId="templateAppId"
          data-testid="template-autocomplete"
        />

        <div class="text-subtitle-2 mb-2 mt-2">Input reference bindings</div>
        <v-row
          v-for="(row, idx) in bindings"
          :key="`binding-${idx}`"
          dense
          align="center"
        >
          <v-col cols="5">
            <v-text-field
              v-model="row.role"
              label="Binding role"
              density="compact"
              variant="outlined"
              placeholder="srcFileAppId"
              spellcheck="false"
            />
          </v-col>
          <v-col cols="6">
            <v-text-field
              v-model="row.appId"
              label="Reference appId"
              density="compact"
              variant="outlined"
              placeholder="reference appId (UUID v7)"
              spellcheck="false"
            />
          </v-col>
          <v-col cols="1">
            <v-btn
              icon="mdi-close"
              size="small"
              variant="text"
              :disabled="bindings.length <= 1"
              @click="removeBinding(idx)"
            />
          </v-col>
        </v-row>
        <v-btn
          size="small"
          variant="text"
          prepend-icon="mdi-plus"
          class="mt-1"
          @click="addBinding"
        >
          Add binding
        </v-btn>

        <v-alert
          v-if="error"
          type="error"
          variant="tonal"
          class="mt-3"
          icon="mdi-alert-circle-outline"
        >
          {{ error }}
        </v-alert>
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn
          color="primary"
          variant="elevated"
          :loading="submitting"
          :disabled="!templateAppId.trim() || submitting"
          prepend-icon="mdi-cog-play-outline"
          @click="submit"
        >
          Materialize
        </v-btn>
      </v-card-actions>
    </v-card>

    <v-card v-if="result" class="mb-6">
      <v-card-title>Derived output</v-card-title>
      <v-card-text>
        <v-row dense>
          <v-col cols="6" sm="4">
            <div class="text-caption text-medium-emphasis">Output kind</div>
            <div class="text-h6">{{ result.outputKind }}</div>
          </v-col>
          <v-col cols="6" sm="8">
            <div class="text-caption text-medium-emphasis">Executor</div>
            <div class="text-h6">{{ result.executor }}</div>
          </v-col>
        </v-row>

        <v-divider class="my-4" />

        <div v-if="result.outputKind === 'REFERENCE'">
          <div class="text-subtitle-2 mb-1">Derived reference appId</div>
          <code data-test="derived-ref-appid">{{ result.derivedReferenceAppId }}</code>
        </div>
        <div v-else>
          <div class="text-subtitle-2 mb-1">View-model</div>
          <pre
            class="text-body-2"
            data-test="view-model-dump"
          >{{ JSON.stringify(result.viewModel ?? {}, null, 2) }}</pre>
        </div>
      </v-card-text>
    </v-card>
  </v-container>
</template>
