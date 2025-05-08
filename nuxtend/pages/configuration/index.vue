<script setup lang="ts">
import {
  ConfigurationFragments,
  ConfigurationMenuEntries,
} from "~/components/context/configuration/configurationMenuItems";

useHead({
  title: "Configuration | shepard",
});

const { routeFragment } = useRouteFragment();
</script>

<template>
  <PaneLayout header="Configuration" :menu-entries="ConfigurationMenuEntries">
    <SemanticRepositoryPane
      v-if="routeFragment === ConfigurationFragments.SEMANTIC_REPOSITORIES"
    />
    <UserGroupsPane
      v-else-if="routeFragment === ConfigurationFragments.USER_GROUPS"
    />
  </PaneLayout>
</template>
