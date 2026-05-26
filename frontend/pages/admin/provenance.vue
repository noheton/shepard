<script setup lang="ts">
/**
 * /admin/provenance — instance-admin provenance dashboard (PROV1e).
 *
 * Standalone page separate from the fragment-routed `/admin#activity-log`
 * pane. Surfaces instance-scoped activity stats (sparkline + histogram +
 * content census) alongside the full activity feed.
 *
 * Admin gate: mirrors `/admin` — non-admins see `UnauthorizedView` after
 * auth resolves; they are never navigated away silently.
 *
 * Design: `aidocs/workflows/55-provenance-and-activity-overhaul.md §PROV1e`
 */

useHead({
  title: "Provenance Dashboard | shepard",
});

const { data, status } = useAuth();

const isInstanceAdmin = computed(() =>
  hasInstanceAdminRole(data.value?.accessToken),
);

const showUnauthorized = computed(
  () =>
    status.value !== "loading" &&
    data.value !== undefined &&
    !isInstanceAdmin.value,
);
</script>

<template>
  <UnauthorizedView
    v-if="showUnauthorized"
    title="Provenance dashboard is restricted"
    message="This section is only available to instance administrators."
    required-role="instance-admin"
  />
  <v-container v-else class="py-6">
    <AdminProvenanceDashboardPane />
  </v-container>
</template>
