<script setup lang="ts">
const endpoint = defineModel<string>("endpoint", {
  required: true,
});

const endpointRules = [
  (value: unknown) => {
    if (value) return true;
    return "Endpoint is required.";
  },
];
</script>

<template>
  <v-text-field
    v-model:model-value="endpoint"
    :rules="endpointRules"
    label="Endpoint*"
    variant="outlined"
    density="compact"
    require
    color="primary"
    hide-details
  />
</template>
