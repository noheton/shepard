<script setup lang="ts">
interface BreadcrumbsProps {
  items: { title: string; to: string }[];
}

defineProps<BreadcrumbsProps>();
</script>

<template>
  <v-breadcrumbs :items="items" class="pl-0">
    <template #divider>
      <div class="text-body-2 text-low-emphasis">/</div>
    </template>
    <template #item="{ item }">
      <v-breadcrumbs-item
        class="text-body-2 pa-0"
        :to="item.to"
        :disabled="item.disabled"
        nuxt
      >
        {{ item.title }}
      </v-breadcrumbs-item>
    </template>
  </v-breadcrumbs>
</template>
