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

import { useStaleRoleSession } from "~/composables/context/useStaleRoleSession";

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

// ROLE-GRANT-STALE-SESSION-02 — upgrade hint copy when middleware saw `role_changed`.
const { reason: staleRoleReason } = useStaleRoleSession();
</script>

<template>
  <UnauthorizedView
    v-if="showUnauthorized"
    title="Provenance dashboard is restricted"
    message="This section is only available to instance administrators."
    required-role="instance-admin"
    :stale-session-reason="staleRoleReason ?? undefined"
  />
  <v-container v-else class="py-6">
    <AdminProvenanceDashboardPane />
  </v-container>
</template>
