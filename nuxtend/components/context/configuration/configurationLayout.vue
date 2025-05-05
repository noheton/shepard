<script setup lang="ts">
import { ConfigurationMenuEntries } from "./configurationMenuItems";

useHead({
  title: "Configuration | shepard",
});
</script>

<template>
  <NuxtLayout name="pane">
    <template #menu>
      <MenuList
        :menu-entries="ConfigurationMenuEntries"
        header="Configuration"
      />
    </template>
    <template #content>
      <slot />
    </template>
  </NuxtLayout>
</template>
