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
  <v-row>
    <v-col class="pb-0">
      <v-text-field
        :model-value="name"
        :rules="nameRules"
        label="Name*"
        variant="outlined"
        density="compact"
        require
        color="primary"
        hide-details
        @update:model-value="newName => emit('nameChanged', newName)"
      />
    </v-col>
  </v-row>
</template>
