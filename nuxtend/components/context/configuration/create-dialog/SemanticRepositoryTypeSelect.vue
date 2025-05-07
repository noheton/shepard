<script setup lang="ts">
import { SemanticRepositoryType } from "@dlr-shepard/backend-client";

const selectedType = defineModel<SemanticRepositoryType>(
  "semanticRepositoryType",
  {
    required: true,
  },
);
</script>

<template>
  <v-select
    v-model="selectedType"
    :items="Object.values(SemanticRepositoryType)"
    color="primary"
    density="compact"
    variant="outlined"
    disabled
  />
</template>
