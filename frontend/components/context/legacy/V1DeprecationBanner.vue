<template>
  <v-snackbar
    v-model="show"
    :timeout="-1"
    location="bottom left"
    color="info"
    class="v1-deprecation-banner"
    multi-line
  >
    <div class="d-flex align-center">
      <v-icon class="me-2">mdi-information-outline</v-icon>
      <div>
        You're using the legacy v1 surface.
        <div class="text-caption text-medium-emphasis">
          {{ v1HitCount }} request{{ v1HitCount === 1 ? '' : 's' }} this
          session went through <code>/shepard/api/...</code>. Disable in
          <strong>admin → Legacy v1</strong> when your tools no longer
          need it. The surface keeps working until you flip it off.
        </div>
      </div>
    </div>
    <template #actions>
      <v-btn variant="text" color="white" @click="dismiss">Dismiss</v-btn>
    </template>
  </v-snackbar>
</template>

<script setup lang="ts">
/**
 * V1COMPAT.0 — frontend deprecation banner.
 *
 * Watches the `X-Shepard-Legacy: true` response header (emitted by
 * the `LegacyV1DeprecationFilter` on every `/shepard/api/...`
 * response) and surfaces a non-alarming, dismissible banner when
 * the current browser session has observed at least one v1 hit.
 *
 * The banner's tone is **informative, not alarming**: shepard's
 * v1 sunset philosophy is "no fork-imposed timeline; operator
 * decides when to flip". This banner exists so the user can see, at
 * a glance, that they (or one of their open tools) is still
 * triggering the v1 surface — useful when migrating a script or
 * client to the `/v2/...` shelf.
 *
 * State lives in `useV1Deprecation()` — a module-level composable
 * so plugins / HTTP layer interception can update the state from
 * outside setup(). The banner is dismissible per-session; the
 * counter keeps incrementing in the background even when dismissed
 * (an admin can re-show by calling `reset()` from the devtools
 * console if needed).
 */

import { computed } from "vue";
import { useV1Deprecation } from "~/composables/context/useV1Deprecation";

const { v1HitCount, visible, dismiss } = useV1Deprecation();

const show = computed({
  get: () => visible.value,
  set: (next: boolean) => {
    if (!next) dismiss();
  },
});
</script>

<style scoped>
.v1-deprecation-banner :deep(code) {
  background: rgba(255, 255, 255, 0.15);
  border-radius: 3px;
  padding: 1px 4px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}
</style>
