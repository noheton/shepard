<script setup lang="ts">
/**
 * NdtGridCanvas — placeholder stub for the MFFD NDT thermography grid mosaic
 * renderer (MFFD-RENDER-NDT-GRID slice 1 of 3).
 *
 * Slice 1 ships the NdtGridShape SHACL + VisNdtGridPluginManifest.
 * Slice 2 ships NdtGridTransformExecutor (S×M×L×F grid resolution from
 *          the bound NDT OTvis Collection).
 * Slice 3 ships the real Canvas 2D mosaic renderer replacing this stub.
 *
 * Until slice 2 is live, the materialize endpoint returns no data — this
 * component renders a placeholder card so the route exists and is
 * reachable from the Collection detail page.
 *
 * Backlog: MFFD-RENDER-NDT-GRID (aidocs/16-dispatcher-backlog.md).
 */

defineProps<{
  /** Collection appId bound in the NdtGridShape template. */
  collectionAppId?: string;
  /** Active colour mode: 'mean-delta-t' | 'pass-fail'. */
  colourMode?: string;
  /** Active colour map name (for mean-delta-t mode). */
  colourMap?: string;
  /** Active layer filter label (e.g. 'L18'). */
  layerFilter?: string;
}>();
</script>

<template>
  <v-card variant="outlined" color="surface-variant" class="pa-4">
    <v-card-title class="text-subtitle-1 font-weight-bold">
      NDT Grid Mosaic
      <v-chip size="x-small" color="warning" class="ml-2">Slice 2 pending</v-chip>
    </v-card-title>
    <v-card-text class="text-body-2 text-medium-emphasis">
      <p>
        The NDT thermography tile-grid renderer is queued in
        <strong>MFFD-RENDER-NDT-GRID slice 2</strong> (executor) and
        <strong>slice 3</strong> (canvas mosaic).
        Once shipped, this pane renders the S×M×L×F grid for collection
        <code>{{ collectionAppId ?? '(unbound)' }}</code>
        in {{ colourMode ?? 'mean-delta-t' }} mode
        <template v-if="layerFilter">(layer filter: {{ layerFilter }})</template>.
      </p>
      <p class="mt-2 text-caption">
        Shape: <code>http://semantics.dlr.de/shepard/transform#NdtGridShape</code>
      </p>
    </v-card-text>
  </v-card>
</template>
