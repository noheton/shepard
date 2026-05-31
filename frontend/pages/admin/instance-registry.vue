<script setup lang="ts">
/**
 * /admin/instance-registry — standalone admin route for the instance registry
 * (FE-PROV-INSTANCE-REGISTRY).
 *
 * Wraps AdminInstanceRegistryPane as a navigable route for deep-links. The
 * same component is also mounted as a fragment inside /admin#instance-registry.
 *
 * Backlog: PLACEHOLDER-REPLACE-FE-PROV-INSTANCE-REGISTRY
 * REST: GET (public) / PATCH (instance-admin) /v2/admin/instances
 */
import AdminInstanceRegistryPane from "~/components/context/admin/AdminInstanceRegistryPane.vue";
import UnauthorizedView from "~/components/layout/UnauthorizedView.vue";

useHead({ title: "Instance Registry | Admin | shepard" });

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
    title="Instance Registry is restricted"
    message="This page is only available to instance administrators."
    required-role="instance-admin"
  />
  <div v-else class="pa-6">
    <AdminInstanceRegistryPane />
  </div>
</template>
