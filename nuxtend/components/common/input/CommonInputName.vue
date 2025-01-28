<script setup lang="ts">
defineProps<{ name: string }>();
const emit = defineEmits<{ (e: "nameChanged", value: string): void }>();

const nameRules = [
  (value: unknown) => {
    if (value) return true;
    return "Name is required.";
  },
];
</script>

<template>
  <v-row class="pt-4 pb-4">
    <v-text-field
      :model-value="name"
      :rules="nameRules"
      label="Name*"
      variant="outlined"
      density="compact"
      require
      @update:model-value="newName => emit('nameChanged', newName)"
    />
  </v-row>
</template>
