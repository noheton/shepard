<script setup lang="ts">
import {
  UserFragments,
  UserMenuEntries,
} from "~/components/context/user/userMenuItems";

useHead({
  title: "User | shepard",
});

const { routeFragment } = useRouteFragment();
</script>

<template>
  <PaneLayout header="User" :menu-entries="UserMenuEntries">
    <ProfilePane v-if="routeFragment === UserFragments.PROFILE" />
  </PaneLayout>
</template>

<style lang="scss" scoped></style>
