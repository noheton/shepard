<script setup lang="ts">
defineProps<{ description: string }>();
const emit = defineEmits<{ (e: "descriptionChanged", value: string): void }>();
</script>

<template>
  <v-row>
    <div class="text-body-1 text-textbody2">Description:</div>
  </v-row>
  <v-row class="pb-4">
    <CommonEditor
      :model-value="description"
      :initial-content="description"
      is-editable
      @update:model-value="
        newDescription => emit('descriptionChanged', newDescription)
      "
    />
  </v-row>
</template>
