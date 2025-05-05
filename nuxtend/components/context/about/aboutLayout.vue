<script setup lang="ts">
import { AboutMenuEntries } from "./aboutMenuItems";

useHead({
  title: "About | shepard",
});
</script>

<template>
  <NuxtLayout name="pane">
    <template #menu>
      <MenuList :menu-entries="AboutMenuEntries" header="About" />
    </template>
    <template #content>
      <slot />
    </template>
  </NuxtLayout>
</template>
