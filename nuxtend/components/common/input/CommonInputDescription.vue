<script setup lang="ts">
defineProps<{ description: string }>();
const emit = defineEmits<{ (e: "descriptionChanged", value: string): void }>();
</script>

<template>
  <v-row>
    <v-col>
      <div class="text-body-1 text-textbody2 pb-2">Description:</div>
      <CommonEditor
        :model-value="description"
        :initial-content="description"
        is-editable
        @update:model-value="
          newDescription => emit('descriptionChanged', newDescription)
        "
      />
    </v-col>
  </v-row>
</template>
