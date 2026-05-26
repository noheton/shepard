<script setup lang="ts">
import { useEventListener } from "@vueuse/core";
import { useAdvancedMode } from "~/composables/context/useAdvancedMode";
import { emitSuccess } from "~/utils/successBus";

const { advancedMode, toggleAdvancedMode } = useAdvancedMode();

// UX-AM1: Ctrl+Shift+D global shortcut to toggle advanced mode.
// Fires from anywhere in the app; skips when focus is inside an <input>
// or <textarea> so typing "D" inside a form field never triggers it.
useEventListener(window, "keydown", (e: KeyboardEvent) => {
  const tag = (e.target as HTMLElement)?.tagName?.toUpperCase();
  if (tag === "INPUT" || tag === "TEXTAREA" || (e.target as HTMLElement)?.isContentEditable) return;
  if (e.ctrlKey && e.shiftKey && e.key === "D") {
    e.preventDefault();
    toggleAdvancedMode().then(() => {
      emitSuccess(advancedMode.value ? "Advanced mode enabled" : "Advanced mode disabled");
    });
  }
});
</script>

<template>
  <v-app>
    <!-- A11y: skip-to-main-content link. Visually hidden until focused;
         becomes visible when a keyboard user tabs to it, allowing them to
         bypass the navigation bar and jump directly into the page content. -->
    <a href="#main-content" class="sr-only sr-only-focusable">Skip to main content</a>
    <HeaderBar />
    <v-main id="main-content" class="bg-canvas">
      <slot />
    </v-main>
    <!-- Sticky bottom-right snackbar when the backend has rolled to a
         new version since this tab loaded. Dismissable per-session. -->
    <StaleBundleBanner />
    <!-- Warns the user 5 minutes before their OIDC access token expires. -->
    <SessionExpiryWarning />
    <!-- V1COMPAT.0 — banner when any response this session has flowed
         through the /shepard/api/... legacy surface (the
         X-Shepard-Legacy: true header set by LegacyV1DeprecationFilter).
         Dormant until a v1 hit happens. Informative tone, dismissible
         per session. Nuxt auto-import resolves the component by its
         bare name via the pathPrefix: false config in nuxt.config.ts. -->
    <V1DeprecationBanner />
  </v-app>
</template>
