<template>
  <v-app>
    <HeaderBar />
    <v-main class="bg-canvas">
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
