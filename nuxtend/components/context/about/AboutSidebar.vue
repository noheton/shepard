<script setup lang="ts">
import { AboutItems } from "./aboutItems";

const currentPage = defineModel<AboutItems>();

const items = [
  {
    value: AboutItems.VERSION,
    icon: "mdi-trending-up",
  },
  {
    value: AboutItems.HEALTH,
    icon: "mdi-check-circle",
  },
  {
    value: AboutItems.DOCUMENTATION,
    icon: "mdi-text-box-multiple-outline",
  },
];
</script>

<template>
  <v-container>
    <v-list class="bg-treeview w-auto">
      <v-list-item class="text-textbody1 text-subtitle-1">About</v-list-item>
      <v-list-item
        v-for="(item, i) in items"
        :key="i"
        :value="item"
        :title="item.value"
        :prepend-icon="item.icon"
        :active="item.value === currentPage"
        @click="() => (currentPage = item.value)"
      />
    </v-list>
  </v-container>
</template>

<style lang="scss" scoped>
.v-list {
  :deep(.v-list-item--active) {
    background-color: rgb(var(--v-theme-focus1));
    border-left: 5px solid rgb(var(--v-theme-primary)) !important;
  }
}
</style>
