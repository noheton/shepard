<script setup lang="ts">
import { SemanticRepositoryType } from "@dlr-shepard/backend-client";
import Select from "~/components/common/Select.vue";

const selectedType = defineModel<SemanticRepositoryType>(
  "semanticRepositoryType",
  {
    required: true,
  },
);
</script>

<template>
  <Select
    v-model="selectedType"
    :items="Object.values(SemanticRepositoryType)"
    color="primary"
    density="compact"
    hide-details="auto"
    variant="outlined"
    disabled
  />
</template>
