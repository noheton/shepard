<script setup lang="ts">
const endpoint = defineModel<string>("endpoint", {
  required: true,
});
</script>

<template>
  <v-text-field
    v-model:model-value="endpoint"
    label="Endpoint*"
    variant="outlined"
    density="compact"
    require
    color="primary"
    hide-details
  />
</template>
