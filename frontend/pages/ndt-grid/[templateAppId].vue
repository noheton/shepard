<script setup lang="ts">
/**
 * /ndt-grid/{templateAppId} — MFFD NDT thermography grid view (MFFD-RENDER-NDT-GRID slice 3).
 *
 * Materializes an NdtGridShape MAPPING_RECIPE template via
 * POST /v2/mappings/{templateAppId}/materialize and renders the
 * NdtGridCanvas mosaic.
 *
 * Entry points:
 *   - In-context: "Open NDT Grid" action on a Collection detail page when a
 *     MAPPING_RECIPE template carrying the NdtGridShape IRI is linked
 *     (primary entry per the "tool entry points are in-context first" rule).
 *   - Direct URL navigation with a known templateAppId.
 *
 * Design: MFFD-RENDER-NDT-GRID (aidocs/16-dispatcher-backlog.md).
 */
import NdtGridCanvas from "~/components/shapes/NdtGridCanvas.vue";
import { materializeMapping } from "~/composables/useMaterializeMapping";
import type { NdtGridEnvelope } from "~/utils/ndtGridCanvas";

useHead({ title: "NDT grid view | shepard" });

const route = useRoute();
const router = useRouter();

const templateAppId = computed(() => String(route.params.templateAppId ?? ""));

const loading = ref(true);
const error = ref<string | null>(null);
const envelope = ref<NdtGridEnvelope | null>(null);
const selectedAppId = ref<string | null>(null);

async function load() {
  if (!templateAppId.value) return;
  loading.value = true;
  error.value = null;
  envelope.value = null;
  try {
    const result = await materializeMapping(templateAppId.value, {});
    if (result.outputKind !== "VIEW" || !result.viewModel) {
      error.value = "This template did not produce a grid view (outputKind was not VIEW).";
      return;
    }
    const env = result.viewModel as unknown as NdtGridEnvelope;
    if (env.kind !== "ndt-grid") {
      error.value = `Expected kind=ndt-grid but received kind=${String(env.kind ?? "unknown")}.`;
      return;
    }
    envelope.value = env;
  } catch (e) {
    error.value =
      e instanceof Error ? e.message : "Failed to load the NDT grid — check the template appId.";
  } finally {
    loading.value = false;
  }
}

onMounted(load);

watch(templateAppId, load);

function onSelectCell(dataObjectAppId: string) {
  selectedAppId.value = dataObjectAppId;
}

/** Navigate to the DataObject detail page if the collection is known from the envelope. */
function openDataObject() {
  if (!selectedAppId.value || !envelope.value?.collectionAppId) return;
  void router.push(
    `/collections/${envelope.value.collectionAppId}/dataobjects/${selectedAppId.value}`,
  );
}
</script>

<template>
  <v-container class="py-6" data-test="ndt-grid-page">
    <div class="d-flex align-center ga-2 mb-5 flex-wrap">
      <v-icon color="primary">mdi-grid</v-icon>
      <h2 class="text-h6">NDT Thermography Grid</h2>
      <v-spacer />
      <v-btn
        v-if="envelope"
        size="small"
        variant="text"
        prepend-icon="mdi-refresh"
        @click="load"
      >
        Refresh
      </v-btn>
    </div>

    <div v-if="loading" class="d-flex justify-center py-10">
      <v-progress-circular indeterminate color="primary" />
    </div>

    <v-alert
      v-else-if="error"
      type="error"
      variant="tonal"
      class="mb-4"
      data-test="ndt-grid-error"
    >
      {{ error }}
    </v-alert>

    <template v-else-if="envelope">
      <NdtGridCanvas :envelope="envelope" @select="onSelectCell" />

      <!-- selected DataObject info panel -->
      <v-card
        v-if="selectedAppId"
        variant="tonal"
        class="mt-4"
        data-test="ndt-selected-panel"
      >
        <v-card-text class="d-flex align-center ga-3 flex-wrap">
          <v-icon size="small" color="primary">mdi-check-circle-outline</v-icon>
          <div>
            <div class="text-caption text-medium-emphasis">Selected DataObject</div>
            <code class="text-body-2" data-test="ndt-selected-appid">{{ selectedAppId }}</code>
          </div>
          <v-spacer />
          <v-btn
            v-if="envelope.collectionAppId"
            size="small"
            color="primary"
            variant="tonal"
            prepend-icon="mdi-open-in-new"
            @click="openDataObject"
          >
            Open DataObject
          </v-btn>
        </v-card-text>
      </v-card>
    </template>
  </v-container>
</template>
