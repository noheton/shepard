<script setup lang="ts">
/**
 * MFFD-RENDER-AFP-THERMO-OVERLAY slice 1 — frontend placeholder stub.
 *
 * Full dual-pane renderer (AFP Trace3D trajectory + OTvis NDT heatmap, synced)
 * lands in slice 3 once the TransformExecutor (slice 2) is shipped.
 * This stub satisfies the CLAUDE.md "ship a UI stub" rule so the backend
 * feature has a navigable frontend surface from day one.
 */
defineProps<{
  afpDataObjectAppId?: string;
  ndtDataObjectAppId?: string;
}>();
</script>

<template>
  <v-card variant="outlined" data-testid="afp-thermo-overlay-placeholder">
    <v-card-title class="d-flex align-center ga-2">
      <v-icon size="small" color="primary">mdi-layers-triple-outline</v-icon>
      <span>AFP Thermo Overlay</span>
      <v-spacer />
      <v-chip size="small" color="warning" variant="tonal">
        Slice 1 — executor pending
      </v-chip>
    </v-card-title>

    <v-card-text>
      <PlaceholderImplStatus
        backend="partial"
        backlog-row="MFFD-RENDER-AFP-THERMO-OVERLAY"
        design-doc="plugins/vis-afp-thermo-overlay/docs/reference.md"
        :endpoint="null"
        notes="Shape declared; AfpThermoOverlayTransformExecutor (slice 2) and AfpThermoOverlayCanvas.vue (slice 3) pending."
      />

      <v-alert
        class="mt-4"
        type="info"
        variant="tonal"
        density="compact"
        icon="mdi-information-outline"
      >
        The AFP Thermo Overlay shows a synced dual-pane view of:
        <ul class="mt-1">
          <li>
            <strong>Left pane:</strong> AFP robot-head 3D trajectory (Trace3D), vertex-coloured
            by a TCP channel (temperature / consolidation-force / head-speed / nip-pressure).
          </li>
          <li>
            <strong>Right pane:</strong> OTvis NDT thermography heatmap for the same
            (Section, Module, Layer) tile — the post-layup inspection result.
          </li>
        </ul>
        The canonical MFFD process-vs-inspection comparison view.
      </v-alert>

      <div v-if="afpDataObjectAppId || ndtDataObjectAppId" class="mt-3 text-caption text-medium-emphasis">
        <div v-if="afpDataObjectAppId">
          AFP DataObject: <code>{{ afpDataObjectAppId }}</code>
        </div>
        <div v-if="ndtDataObjectAppId">
          NDT DataObject: <code>{{ ndtDataObjectAppId }}</code>
        </div>
      </div>
    </v-card-text>
  </v-card>
</template>
