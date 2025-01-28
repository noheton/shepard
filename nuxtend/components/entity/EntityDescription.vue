<script setup lang="ts">
interface EntityDescriptionProps {
  entity: { description?: string | null };
}
defineProps<EntityDescriptionProps>();
</script>

<template>
  <v-container fluid class="pa-0">
    <v-row v-if="!!entity.description" no-gutters>
      <CommonEditor
        :model-value="entity.description"
        :is-editable="false"
        :initial-content="entity.description"
      />
    </v-row>
  </v-container>
</template>
