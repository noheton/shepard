<script setup lang="ts">
import {
  UserFragments,
  UserMenuEntries,
} from "~/components/context/user/userMenuItems";
import SubscriptionsPane from "~/components/context/user/SubscriptionsPane.vue";
import GitCredentialsPane from "~/components/context/user/GitCredentialsPane.vue";
import McpPane from "~/components/context/user/McpPane.vue";

useHead({
  title: "Profile | shepard",
});

const { routeFragment } = useRouteFragment();
</script>

<template>
  <PaneLayout header="Profile" :menu-entries="UserMenuEntries">
    <ProfilePane v-if="routeFragment === UserFragments.PROFILE" />
    <ApiKeyPane v-if="routeFragment === UserFragments.API_KEYS" />
    <McpPane v-if="routeFragment === UserFragments.MCP" />
    <SubscriptionsPane v-if="routeFragment === UserFragments.SUBSCRIPTIONS" />
    <GitCredentialsPane v-if="routeFragment === UserFragments.GIT_CREDENTIALS" />
  </PaneLayout>
</template>
