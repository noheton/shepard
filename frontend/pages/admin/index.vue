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

useHead({
  title: "Admin | shepard",
});

const { data } = useAuth();

const isInstanceAdmin = computed(() =>
  hasInstanceAdminRole(data.value?.accessToken),
);

// Redirect non-admins to /me
watchEffect(() => {
  if (data.value !== undefined && !isInstanceAdmin.value) {
    navigateTo("/me");
  }
});

const { routeFragment } = useRouteFragment();
</script>

<template>
  <PaneLayout header="Admin" :menu-entries="AdminMenuEntries">
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
  </PaneLayout>
</template>
