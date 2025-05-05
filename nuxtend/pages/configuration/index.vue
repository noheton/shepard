<script setup lang="ts">
import { ConfigurationFragments } from "~/components/context/configuration/configurationMenuItems";

const { routeFragment } = useRouteFragment();
</script>

<template>
  <ConfigurationLayout>
    <SemanticRepositoryPane
      v-if="routeFragment === ConfigurationFragments.SEMANTIC_REPOSITORIES"
    />
  </ConfigurationLayout>
</template>
