<script setup lang="ts">
import {
  AdminFragments,
  AdminMenuEntries,
} from "~/components/context/admin/adminMenuItems";
import SemanticRepositoryPane from "~/components/context/configuration/SemanticRepositoryPane.vue";
import UserGroupsPane from "~/components/context/configuration/UserGroupsPane.vue";
import InstanceRorPane from "~/components/context/admin/InstanceRorPane.vue";
import AdminStoragePane from "~/components/context/admin/AdminStoragePane.vue";
import PluginsAdminPane from "~/components/context/admin/PluginsAdminPane.vue";
import PermissionAuditLogPane from "~/components/context/admin/PermissionAuditLogPane.vue";
import UnhideAdminPane from "~/components/context/admin/UnhideAdminPane.vue";
import AdminLegacyV1Pane from "~/components/context/admin/AdminLegacyV1Pane.vue";
import SectionIndexLanding from "~/components/layout/SectionIndexLanding.vue";
import UnauthorizedView from "~/components/layout/UnauthorizedView.vue";

useHead({
  title: "Admin | shepard",
});

const { data, status } = useAuth();

const isInstanceAdmin = computed(() =>
  hasInstanceAdminRole(data.value?.accessToken),
);

// UI-2026-05-24-004 — replaces the silent navigateTo("/me") with an
// explicit Unauthorized view that keeps the URL stable. Only render the
// unauth view once we're sure auth has resolved (status !== loading), else
// the page would flash "Unauthorized" before the role check is ready.
const showUnauthorized = computed(
  () =>
    status.value !== "loading" &&
    data.value !== undefined &&
    !isInstanceAdmin.value,
);

const { routeFragment } = useRouteFragment();

// SectionIndexLanding fallback (UI-2026-05-24-003) — shown when admin lands
// on /admin with no hash set. Mirrors AdminMenuEntries.
const landingCards = [
  {
    fragment: AdminFragments.FEATURE_TOGGLES,
    icon: "mdi-toggle-switch-outline",
    title: "Feature Toggles",
    description: "Flip runtime feature flags without a restart.",
  },
  {
    fragment: AdminFragments.PLUGINS,
    icon: "mdi-puzzle-outline",
    title: "Plugins",
    description: "Inspect installed plugins, their versions, and health.",
  },
  {
    fragment: AdminFragments.INSTANCE_HEALTH,
    icon: "mdi-heart-pulse",
    title: "Instance Health",
    description: "Self-observability metrics: ingest rates, payload kinds, substrates.",
  },
  {
    fragment: AdminFragments.STORAGE_OVERVIEW,
    icon: "mdi-database-eye-outline",
    title: "Storage Overview",
    description: "Per-substrate capacity, orphans, and retention policy state.",
  },
  {
    fragment: AdminFragments.TEMPLATES,
    icon: "mdi-file-document-multiple-outline",
    title: "Templates",
    description: "Instance-wide DataObject and Collection templates.",
  },
  {
    fragment: AdminFragments.SEMANTIC_REPOSITORIES,
    icon: "mdi-library-outline",
    title: "Semantic Repositories",
    description: "Manage ontologies seeded into the internal Fuseki store.",
  },
  {
    fragment: AdminFragments.USER_GROUPS,
    icon: "mdi-account-multiple-outline",
    title: "User Groups",
    description: "Group memberships that drive permission inheritance.",
  },
  {
    fragment: AdminFragments.INSTANCE_ROR,
    icon: "mdi-office-building",
    title: "Research Organization",
    description: "This instance's ROR ID — surfaces in FAIR exports.",
  },
  {
    fragment: AdminFragments.PERMISSION_AUDIT_LOG,
    icon: "mdi-shield-account-outline",
    title: "Permission Audit Log",
    description: "Who granted, revoked, or changed permissions, when.",
  },
  {
    fragment: AdminFragments.UNHIDE,
    icon: "mdi-web-sync",
    title: "Unhide",
    description: "Helmholtz Unhide publish-and-harvest integration config.",
  },
  {
    fragment: AdminFragments.LEGACY_V1,
    icon: "mdi-history",
    title: "Legacy v1",
    description: "v1 surface deprecation knobs and per-operator sunset state.",
  },
];
</script>

<template>
  <UnauthorizedView
    v-if="showUnauthorized"
    title="Administration is restricted"
    message="This section is only available to instance administrators. If you need access, ask an instance admin to grant you the instance-admin role."
    required-role="instance-admin"
  />
  <PaneLayout v-else header="Admin" :menu-entries="AdminMenuEntries">
    <SectionIndexLanding
      v-if="!routeFragment"
      title="Administration"
      subtitle="Instance configuration, plugins, integrations, and audit trails."
      :sections="landingCards"
    />
    <FeatureTogglesPane
      v-if="routeFragment === AdminFragments.FEATURE_TOGGLES"
    />
    <PluginsAdminPane v-if="routeFragment === AdminFragments.PLUGINS" />
    <AdminMetricsCard
      v-if="routeFragment === AdminFragments.INSTANCE_HEALTH"
    />
    <AdminTemplatesPane
      v-if="routeFragment === AdminFragments.TEMPLATES"
    />
    <SemanticRepositoryPane
      v-if="routeFragment === AdminFragments.SEMANTIC_REPOSITORIES"
    />
    <UserGroupsPane
      v-if="routeFragment === AdminFragments.USER_GROUPS"
    />
    <InstanceRorPane
      v-if="routeFragment === AdminFragments.INSTANCE_ROR"
    />
    <AdminStoragePane
      v-if="routeFragment === AdminFragments.STORAGE_OVERVIEW"
    />
    <PermissionAuditLogPane
      v-if="routeFragment === AdminFragments.PERMISSION_AUDIT_LOG"
    />
    <UnhideAdminPane v-if="routeFragment === AdminFragments.UNHIDE" />
    <AdminLegacyV1Pane v-if="routeFragment === AdminFragments.LEGACY_V1" />
  </PaneLayout>
</template>
