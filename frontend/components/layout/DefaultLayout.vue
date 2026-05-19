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
  </v-app>
</template>
