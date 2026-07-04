<script setup lang="ts">
/**
 * /tools/materialize-mapping — V2CONV-B3-FE MAPPING_RECIPE materializer.
 *
 * UI-GAP-1 slice 1: replaced bare templateAppId text-field with
 * TemplateAutocomplete scoped to MAPPING_RECIPE kind.
 * UI-GAP-1 slice 2: replaced free-text appId field in each binding row with a
 * v-combobox backed by useFetchReferenceOptions. When the page arrives with a
 * focusDataObjectAppId route query param (set by the in-context do-materialize
 * entry), references from that DataObject are offered as picker suggestions;
 * the combobox still accepts raw appId text so the flow works without context.
 * UI-GAP-1 slice 3: output dispatch — REFERENCE outputs show a CopyableAppIdChip
 * + "Reference created" badge; VIEW outputs dispatch to the appropriate renderer
 * (scene-graph play page for URDF envelopes, collapsible raw dump otherwise).
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
import { useFetchReferenceOptions } from "~/composables/useFetchReferenceOptions";

useHead({ title: "Materialize mapping | shepard" });

const route = useRoute();

interface BindingRow {
  role: string;
  appId: string;
}

// Pre-populate from in-context route query params (do-materialize entry).
const templateAppId = ref<string>(
  typeof route.query.templateAppId === "string" ? route.query.templateAppId : "",
);
const focusDataObjectAppId = ref<string | undefined>(
  typeof route.query.focusDataObjectAppId === "string"
    ? route.query.focusDataObjectAppId
    : undefined,
);

const bindings = ref<BindingRow[]>([{ role: "srcFileAppId", appId: "" }]);
const submitting = ref<boolean>(false);
const result = ref<MaterializeResponse | null>(null);
const error = ref<string | null>(null);

// Reference options for the binding comboboxes — populated from focusDataObjectAppId.
const { options: referenceOptions, isLoading: refOptionsLoading } =
  useFetchReferenceOptions(focusDataObjectAppId);

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

/**
 * Returns true when the VIEW result's viewModel is a SceneGraphPlay envelope
 * (produced by the vis-trace3d SceneGraphPlayTransformExecutor). Discriminates
 * by urdfFileReferenceAppId presence — the only executor that writes this
 * field is SceneGraphPlayTransformExecutor, so any URDF-bound executor
 * (including third-party ones) routes to the play page automatically.
 */
function hasSceneGraphPlay(r: MaterializeResponse): boolean {
  const vm = r.viewModel as Record<string, unknown> | null | undefined;
  return r.outputKind === "VIEW" && typeof vm?.urdfFileReferenceAppId === "string";
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

        <!-- Context chip: shown when arriving from a DataObject's do-materialize action. -->
        <v-chip
          v-if="focusDataObjectAppId"
          class="mb-3"
          color="primary"
          variant="tonal"
          size="small"
          prepend-icon="mdi-link-variant"
          data-testid="focus-do-chip"
        >
          References from DataObject {{ focusDataObjectAppId.slice(0, 8) }}…
        </v-chip>

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
            <!--
              UI-GAP-1 slice 2: v-combobox shows references from the focused
              DataObject as suggestions; free-text entry still accepted so the
              flow works without an in-context DataObject.
            -->
            <v-combobox
              v-model="row.appId"
              :items="referenceOptions"
              :loading="refOptionsLoading"
              item-value="appId"
              item-title="label"
              label="Reference"
              density="compact"
              variant="outlined"
              placeholder="pick a reference or paste an appId"
              spellcheck="false"
              clearable
              data-testid="reference-combobox"
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

        <!--
          UI-GAP-1 slice 3: output dispatch.
          REFERENCE → copyable chip + badge.
          VIEW → scene-graph play page (URDF envelope) or generic alert + raw dump.
        -->
        <div v-if="result.outputKind === 'REFERENCE'">
          <div class="text-subtitle-2 mb-2">Derived reference</div>
          <div class="d-flex align-center flex-wrap ga-2">
            <CopyableAppIdChip
              :app-id="result.derivedReferenceAppId ?? ''"
              testid="derived-ref-appid"
            />
            <v-chip
              size="small"
              color="success"
              variant="tonal"
              prepend-icon="mdi-check-circle-outline"
            >
              Reference created
            </v-chip>
          </div>
          <div class="text-caption text-medium-emphasis mt-2">
            Click the chip to copy the appId. Use this reference as an input binding in another recipe.
          </div>
        </div>

        <div v-else-if="result.outputKind === 'VIEW'">
          <div class="text-subtitle-2 mb-2">View output</div>
          <!--
            Scene-graph dispatch: when the viewModel carries urdfFileReferenceAppId
            the vis-trace3d executor produced a play envelope — navigate to the
            /scene-graphs/play page which re-materializes and renders via UrdfCanvas.
          -->
          <v-btn
            v-if="hasSceneGraphPlay(result)"
            color="primary"
            variant="elevated"
            prepend-icon="mdi-cube-scan"
            :to="`/scene-graphs/play/${result.templateAppId}`"
            data-testid="open-in-scene-graph-btn"
            class="mb-3"
          >
            Open 3D view
          </v-btn>
          <v-alert
            v-else
            type="info"
            variant="tonal"
            density="compact"
            class="mb-3"
          >
            No dedicated renderer registered for executor
            <code>{{ result.executor }}</code>. Raw view model below.
          </v-alert>
          <v-expansion-panels variant="accordion">
            <v-expansion-panel>
              <v-expansion-panel-title class="text-caption text-medium-emphasis">
                Raw view model (debug)
              </v-expansion-panel-title>
              <v-expansion-panel-text>
                <pre class="text-body-2" data-testid="view-model-dump">{{ JSON.stringify(result.viewModel ?? {}, null, 2) }}</pre>
              </v-expansion-panel-text>
            </v-expansion-panel>
          </v-expansion-panels>
        </div>

        <div v-else>
          <v-alert type="warning" variant="tonal" density="compact">
            Unknown output kind: <code>{{ result.outputKind }}</code>
          </v-alert>
        </div>
      </v-card-text>
    </v-card>
  </v-container>
</template>
