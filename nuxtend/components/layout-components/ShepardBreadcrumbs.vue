<script setup lang="ts">
import type { VBreadcrumbs } from "vuetify/components";

interface ShepardBreadcrumbsProps {
  items: { title: string; to: string }[];
}

defineProps<ShepardBreadcrumbsProps>();
</script>

<template>
  <v-breadcrumbs :items="items" class="pl-0">
    <template #divider>
      <div class="text-body-1 text-low-emphasis">/</div>
    </template>
    <template #item="{ item }">
      <v-breadcrumbs-item
        class="pa-0"
        :to="item.to"
        :disabled="item.disabled"
        nuxt
      >
        {{ item.title }}
      </v-breadcrumbs-item>
    </template>
  </v-breadcrumbs>
</template>
