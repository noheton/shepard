<script setup lang="ts">
import type { BasicEntity } from "@dlr-shepard/backend-client";

defineProps<{
  collection: BasicEntity;
}>();
</script>

<template>
  <v-card-title>
    <h4>{{ collection.name }}</h4>
    ID: {{ collection.id }}
  </v-card-title>
  <v-card-subtitle>
    {{
      "created at " +
      collection.createdAt?.toDateString() +
      " by " +
      collection.createdBy
    }}
  </v-card-subtitle>
</template>
