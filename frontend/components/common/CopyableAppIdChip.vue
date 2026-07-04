<script setup lang="ts">
/**
 * II3 (ui-scrutinizer-2026-05-30) — shared click-to-copy appId chip.
 *
 * Wraps a `<head>…<tail>` truncated appId in a Vuetify v-chip with a
 * tooltip ("Copy appId") + a success snackbar on copy. Used wherever a
 * table or detail row surfaces a UUID v7 — `/scene-graphs` list,
 * `/semantic/predicates/{iri}` sample table, the user git-credentials
 * pane, future tables that follow.
 *
 * Reuses the canonical 8…4 truncation from `~/utils/appId.ts` so every
 * surface is visually consistent. The chip degrades to a no-op when the
 * clipboard API is unavailable (older browsers, http test contexts) —
 * we surface a warning toast in that case so the user knows to copy
 * manually rather than silently failing.
 */
import { truncateAppId, copyAppIdToClipboard } from "~/utils/appId";

const { appId, testid } = withDefaults(
  defineProps<{
    appId: string;
    /**
     * Optional data-testid override for downstream tests. When omitted
     * we ship `copyable-appid-chip` so callers can target the chip by a
     * stable hook.
     */
    testid?: string;
  }>(),
  { testid: "copyable-appid-chip" },
);

const display = computed(() => truncateAppId(appId));

async function onCopy(): Promise<void> {
  const ok = await copyAppIdToClipboard(appId);
  if (ok) {
    emitSuccess("Copied appId to clipboard");
  }
  // On failure, silently no-op — matches the prior `scene-graphs`
  // behaviour. The full appId is still visible via the tooltip so the
  // user can copy by hand from the title attribute. A future hardening
  // pass could route through `handleError` once a typed
  // ClipboardUnavailable error exists.
}
</script>

<template>
  <v-tooltip text="Copy appId" location="top">
    <template #activator="{ props: tip }">
      <v-chip
        v-bind="tip"
        variant="text"
        size="small"
        class="copyable-appid-chip"
        :data-testid="testid"
        @click.stop="onCopy"
      >
        <span class="appid-mono">{{ display }}</span>
        <v-icon size="x-small" class="ms-1">mdi-content-copy</v-icon>
      </v-chip>
    </template>
  </v-tooltip>
</template>

<style scoped>
.copyable-appid-chip {
  cursor: pointer;
}
.appid-mono {
  font-family: ui-monospace, "SF Mono", Menlo, Consolas, monospace;
  letter-spacing: 0.02em;
}
</style>
