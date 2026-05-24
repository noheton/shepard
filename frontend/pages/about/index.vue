<script setup lang="ts">
import {
  AboutFragments,
  AboutMenuEntries,
} from "~/components/context/about/aboutMenuItems";
import PaneLayout from "~/components/layout/PaneLayout.vue";
import SectionIndexLanding from "~/components/layout/SectionIndexLanding.vue";

useHead({
  title: "About | shepard",
});

const { routeFragment } = useRouteFragment();

// SectionIndexLanding fallback (UI-2026-05-24-003) — shown when no hash is set.
const landingCards = [
  {
    fragment: AboutFragments.VERSION,
    icon: "mdi-information-outline",
    title: "Version",
    description: "Build version, commit, and runtime details for this instance.",
  },
  {
    fragment: AboutFragments.ORGANIZATION,
    icon: "mdi-domain",
    title: "Organization",
    description: "The research organization operating this shepard instance.",
  },
  {
    fragment: AboutFragments.HEALTH,
    icon: "mdi-check-circle-outline",
    title: "System Health",
    description: "Live status of the substrates shepard depends on.",
  },
  {
    fragment: AboutFragments.DOCUMENTATION,
    icon: "mdi-file-document-outline",
    title: "Documentation",
    description: "Links to the user guide, admin docs, and plugin reference.",
  },
];
</script>

<template>
  <PaneLayout header="About" :menu-entries="AboutMenuEntries">
    <SectionIndexLanding
      v-if="!routeFragment"
      title="About shepard"
      subtitle="Instance metadata, version, system health, and documentation."
      :sections="landingCards"
    />
    <VersionPane v-if="routeFragment === AboutFragments.VERSION" />
    <OrganizationPane v-if="routeFragment === AboutFragments.ORGANIZATION" />
    <HealthPane v-if="routeFragment === AboutFragments.HEALTH" />
    <DocumentationPane v-if="routeFragment === AboutFragments.DOCUMENTATION" />
  </PaneLayout>
</template>
