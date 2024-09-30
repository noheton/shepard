<script setup lang="ts">
import type { Collection } from "@dlr-shepard/backend-client";

defineProps<{
  collection: Collection;
}>();
</script>

<template>
  <v-card-title>
    <h4>{{ collection.name }}</h4>
    ID: {{ collection.id }}
  </v-card-title>
  <v-card-subtitle>
    {{ "created at " + collection.createdAt + " by " + collection.createdBy }}
  </v-card-subtitle>
</template>
