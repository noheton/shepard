<script setup lang="ts">
import EntityEditorReadonly from "./EntityEditorReadonly.vue";

interface EntityDescriptionProps {
  entity: { description?: string | null };
}
defineProps<EntityDescriptionProps>();
</script>

<template>
  <v-container fluid class="pa-0">
    <v-row v-if="!!entity.description" no-gutters>
      <EntityEditorReadonly :content="entity.description" />
    </v-row>
  </v-container>
</template>
