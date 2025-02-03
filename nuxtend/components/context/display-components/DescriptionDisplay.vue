<script setup lang="ts">
import RichTextEditor from "~/components/common/editor/RichTextEditor.vue";

interface DescriptionDisplayProps {
  entity: { description?: string | null };
}
defineProps<DescriptionDisplayProps>();
</script>

<template>
  <v-container fluid class="pa-0">
    <v-row v-if="!!entity.description" no-gutters>
      <RichTextEditor
        :model-value="entity.description"
        :is-editable="false"
        :initial-content="entity.description"
      />
    </v-row>
  </v-container>
</template>
