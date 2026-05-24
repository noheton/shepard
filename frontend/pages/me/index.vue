<script setup lang="ts">
import {
  UserFragments,
  UserMenuEntries,
} from "~/components/context/user/userMenuItems";
import SubscriptionsPane from "~/components/context/user/SubscriptionsPane.vue";
import GitCredentialsPane from "~/components/context/user/GitCredentialsPane.vue";
import McpPane from "~/components/context/user/McpPane.vue";
import PlaceholderFragmentPane from "~/components/common/placeholder/PlaceholderFragmentPane.vue";
import SectionIndexLanding from "~/components/layout/SectionIndexLanding.vue";

useHead({
  title: "Profile | shepard",
});

const { routeFragment } = useRouteFragment();

// SectionIndexLanding fallback (UI-2026-05-24-003) — shown when no hash is set.
const landingCards = [
  {
    fragment: UserFragments.PROFILE,
    icon: "mdi-account-outline",
    title: "Profile",
    description: "Your display name, avatar, and account information.",
  },
  {
    fragment: UserFragments.API_KEYS,
    icon: "mdi-key-outline",
    title: "API Keys",
    description: "Personal access tokens for scripting against the shepard API.",
  },
  {
    fragment: UserFragments.MCP,
    icon: "mdi-robot-outline",
    title: "MCP",
    description: "Connect Claude or another MCP client to your shepard data.",
  },
  {
    fragment: UserFragments.SUBSCRIPTIONS,
    icon: "mdi-bell-outline",
    title: "Subscriptions",
    description: "Manage which containers you receive notifications for.",
  },
  {
    fragment: UserFragments.GIT_CREDENTIALS,
    icon: "mdi-source-repository",
    title: "Git Credentials",
    description: "Personal access tokens for git providers used by importers.",
  },
  // ----- placeholder card (no-UI-gap roll-out 2026-05-24) -----
  {
    fragment: UserFragments.AI_SETTINGS,
    icon: "mdi-robot-outline",
    title: "AI Settings",
    description: "Personal LLM provider config — base URL, model, API key (placeholder).",
  },
];
</script>

<template>
  <PaneLayout header="Profile" :menu-entries="UserMenuEntries">
    <SectionIndexLanding
      v-if="!routeFragment"
      title="My profile"
      subtitle="Account settings, integrations, and notifications."
      :sections="landingCards"
    />
    <ProfilePane v-if="routeFragment === UserFragments.PROFILE" />
    <ApiKeyPane v-if="routeFragment === UserFragments.API_KEYS" />
    <McpPane v-if="routeFragment === UserFragments.MCP" />
    <SubscriptionsPane v-if="routeFragment === UserFragments.SUBSCRIPTIONS" />
    <GitCredentialsPane v-if="routeFragment === UserFragments.GIT_CREDENTIALS" />
    <!-- placeholder pane (no-UI-gap roll-out 2026-05-24) -->
    <PlaceholderFragmentPane
      v-if="routeFragment === UserFragments.AI_SETTINGS"
      slug="ai-settings"
    />
  </PaneLayout>
</template>
