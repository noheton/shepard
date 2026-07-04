<script setup lang="ts">
/**
 * /afp-thermo-overlay/{templateAppId} — MFFD AFP vs NDT dual-pane overlay.
 *
 * Materializes an AfpThermoOverlay MAPPING_RECIPE template via
 * POST /v2/mappings/{templateAppId}/materialize and renders the
 * AfpThermoOverlayCanvas (AFP robot-head course metadata left pane,
 * OTvis NDT inspection tile metadata right pane, tile-match verdict header).
 *
 * Entry points:
 *   - In-context: "Open AFP Thermo Overlay" action on a Collection detail
 *     page when a MAPPING_RECIPE template carrying the AfpThermoOverlayShape
 *     IRI is linked (primary entry per the "tool entry points are in-context
 *     first" rule). The Collection appId is forwarded as ?collectionAppId=…
 *     so DataObject deep-links resolve correctly.
 *   - Direct URL navigation with a known templateAppId.
 *
 * Design: MFFD-RENDER-AFP-THERMO-OVERLAY (aidocs/16-dispatcher-backlog.md).
 * Phase 2 (live Trace3D + ThermographyCanvas embeds): MFFD-RENDER-AFP-THERMO-OVERLAY-4.
 */
import AfpThermoOverlayCanvas from "~/components/mffd/AfpThermoOverlayCanvas.vue";
import { materializeMapping } from "~/composables/useMaterializeMapping";
import type { AfpThermoOverlayEnvelope } from "~/utils/afpThermoOverlay";

useHead({ title: "AFP Thermo Overlay | shepard" });

const route = useRoute();
const router = useRouter();

const templateAppId = computed(() => String(route.params.templateAppId ?? ""));
const collectionAppId = computed(() =>
  typeof route.query.collectionAppId === "string" ? route.query.collectionAppId : undefined,
);

const loading = ref(true);
const error = ref<string | null>(null);
const envelope = ref<AfpThermoOverlayEnvelope | null>(null);

async function load() {
  if (!templateAppId.value) return;
  loading.value = true;
  error.value = null;
  envelope.value = null;
  try {
    const result = await materializeMapping(templateAppId.value, {});
    if (result.outputKind !== "VIEW" || !result.viewModel) {
      error.value = "This template did not produce an overlay view (outputKind was not VIEW).";
      return;
    }
    const env = result.viewModel as unknown as AfpThermoOverlayEnvelope;
    if (env.kind !== "afp-thermo-overlay") {
      error.value = `Expected kind=afp-thermo-overlay but received kind=${String(env.kind ?? "unknown")}.`;
      return;
    }
    envelope.value = env;
  } catch (e) {
    error.value =
      e instanceof Error
        ? e.message
        : "Failed to load the AFP thermo overlay — check the template appId.";
  } finally {
    loading.value = false;
  }
}

onMounted(load);

watch(templateAppId, load);
</script>

<template>
  <v-container class="py-6" data-testid="afp-thermo-overlay-page">
    <div class="d-flex align-center ga-3 mb-6">
      <v-btn
        icon="mdi-arrow-left"
        variant="text"
        size="small"
        @click="router.back()"
      />
      <v-icon color="primary">mdi-layers-triple-outline</v-icon>
      <h2 class="text-h6">AFP Thermo Overlay</h2>
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
      data-testid="afp-thermo-overlay-error"
    >
      {{ error }}
    </v-alert>

    <AfpThermoOverlayCanvas
      v-else-if="envelope"
      :envelope="envelope"
      :collection-app-id="collectionAppId"
    />
  </v-container>
</template>
