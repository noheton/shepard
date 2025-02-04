<script setup lang="ts">
const name = defineModel<string>("name", {
  required: true,
});

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
        v-model:model-value="name"
        :rules="nameRules"
        label="Name*"
        variant="outlined"
        density="compact"
        require
        color="primary"
        hide-details
      />
    </v-col>
  </v-row>
</template>
