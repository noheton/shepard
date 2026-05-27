<script setup lang="ts">
/**
 * /admin/instance-registry — standalone admin page for the instance registry
 * (FE-PROV-INSTANCE-REGISTRY).
 *
 * Wraps the fragment-pane version as a standalone navigable route for
 * deep-links. Admin gate: non-admins see UnauthorizedView.
 *
 * Backlog: FE-PROV-INSTANCE-REGISTRY
 * REST: GET/PATCH /v2/admin/instances
 */
import PlaceholderPageHeader from "~/components/common/placeholder/PlaceholderPageHeader.vue";
import PlaceholderRestDump from "~/components/common/placeholder/PlaceholderRestDump.vue";
import PlaceholderImplStatus from "~/components/common/placeholder/PlaceholderImplStatus.vue";
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
  <div v-else class="pa-6 d-flex flex-column ga-4">
    <PlaceholderPageHeader
      title="Instance Registry"
      subtitle="Register peer Shepard instances — resolves instanceId to friendly display name in the provenance badge hover."
    />
    <PlaceholderImplStatus
      backend="shipped"
      backlog-row="FE-PROV-INSTANCE-REGISTRY"
      design-doc="aidocs/16-dispatcher-backlog.md"
      endpoint="/v2/admin/instances"
      notes="Backend shipped (GET/PATCH /v2/admin/instances). Full management UI queued. Raw REST dump available in advanced mode."
    />
    <PlaceholderRestDump
      endpoint="/v2/admin/instances"
      hint="Current instance registry — GET /v2/admin/instances (public endpoint)."
    />
  </div>
</template>
