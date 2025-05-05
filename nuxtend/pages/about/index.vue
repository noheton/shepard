<script setup lang="ts">
import { AboutFragments } from "~/components/context/about/aboutMenuItems";

useHead({
  title: "About | shepard",
});

const { routeFragment } = useRouteFragment();
</script>

<template>
  <AboutLayout>
    <VersionPane v-if="routeFragment === AboutFragments.VERSION" />
    <HealthPane v-if="routeFragment === AboutFragments.HEALTH" />
    <DocumentationPane v-if="routeFragment === AboutFragments.DOCUMENTATION" />
  </AboutLayout>
</template>
