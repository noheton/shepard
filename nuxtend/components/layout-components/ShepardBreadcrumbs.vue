<script setup lang="ts">
import type { VBreadcrumbs } from "vuetify/components";

interface ShepardBreadcrumbsProps {
  items: VBreadcrumbs["$props"]["items"];
}

defineProps<ShepardBreadcrumbsProps>();
</script>

<template>
  <v-breadcrumbs :items="items">
    <template #divider>
      <div class="text-body-1 text-grey-200">/</div>
    </template>
  </v-breadcrumbs>
</template>
