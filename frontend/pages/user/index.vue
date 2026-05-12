<script setup lang="ts">
import {
  UserFragments,
  UserMenuEntries,
} from "~/components/context/user/userMenuItems";
import SubscriptionsPane from "~/components/context/user/SubscriptionsPane.vue";
import GitCredentialsPane from "~/components/context/user/GitCredentialsPane.vue";

useHead({
  title: "User | shepard",
});

const { routeFragment } = useRouteFragment();
</script>

<template>
  <PaneLayout header="User" :menu-entries="UserMenuEntries">
    <ProfilePane v-if="routeFragment === UserFragments.PROFILE" />
    <ApiKeyPane v-if="routeFragment === UserFragments.API_KEYS" />
    <SubscriptionsPane v-if="routeFragment === UserFragments.SUBSCRIPTIONS" />
    <GitCredentialsPane v-if="routeFragment === UserFragments.GIT_CREDENTIALS" />
  </PaneLayout>
</template>

<style lang="scss" scoped></style>
