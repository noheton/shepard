<script setup lang="ts">
import {
  AboutFragments,
  AboutMenuEntries,
} from "~/components/context/about/aboutMenuItems";
import PaneLayout from "~/components/layout/PaneLayout.vue";

useHead({
  title: "About | shepard",
});

const { routeFragment } = useRouteFragment();
</script>

<template>
  <PaneLayout header="About" :menu-entries="AboutMenuEntries">
    <VersionPane v-if="routeFragment === AboutFragments.VERSION" />
    <OrganizationPane v-if="routeFragment === AboutFragments.ORGANIZATION" />
    <HealthPane v-if="routeFragment === AboutFragments.HEALTH" />
    <DocumentationPane v-if="routeFragment === AboutFragments.DOCUMENTATION" />
  </PaneLayout>
</template>
