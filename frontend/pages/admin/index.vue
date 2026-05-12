<script setup lang="ts">
import {
  AdminFragments,
  AdminMenuEntries,
} from "~/components/context/admin/adminMenuItems";

useHead({
  title: "Admin | shepard",
});

const { data } = useAuth();

const isInstanceAdmin = computed(() =>
  hasInstanceAdminRole(data.value?.accessToken),
);

// Redirect non-admins to /user
watchEffect(() => {
  if (data.value !== undefined && !isInstanceAdmin.value) {
    navigateTo("/user");
  }
});

const { routeFragment } = useRouteFragment();
</script>

<template>
  <PaneLayout header="Admin" :menu-entries="AdminMenuEntries">
    <FeatureTogglesPane
      v-if="routeFragment === AdminFragments.FEATURE_TOGGLES"
    />
  </PaneLayout>
</template>
