<script setup lang="ts">
import type { BasicEntity } from "@dlr-shepard/backend-client";

defineProps<{
  entity: BasicEntity;
}>();
</script>

<template>
  <v-card-title>
    <h4>{{ entity.name }}</h4>
    ID: {{ entity.id }}
  </v-card-title>
  <v-card-subtitle>
    {{
      "created at " +
      entity.createdAt.toDateString() +
      " by " +
      entity.createdBy
    }}
  </v-card-subtitle>
</template>
