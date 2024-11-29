<script setup lang="ts">
interface EntityDescriptionProps {
  entity: { description?: string | null };
}
defineProps<EntityDescriptionProps>();
</script>

<template>
  <v-container fluid class="pa-0">
    <v-row no-gutters class="text-body-2 text-black-500">
      {{ entity.description }}
    </v-row>
  </v-container>
</template>
