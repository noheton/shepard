<script setup lang="ts">
import type { MenuListProps } from "~/components/common/pane-menu/MenuList.vue";

defineProps<MenuListProps>();
</script>

<template>
  <NuxtLayout name="pane">
    <template #menu>
      <MenuList :menu-entries="menuEntries" :header="header" />
    </template>
    <template #content>
      <slot />
    </template>
  </NuxtLayout>
</template>
