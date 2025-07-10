<script lang="ts" setup>
import { TraversalRules } from "@dlr-shepard/backend-client";

defineProps<{ collectionId: number }>();

const traversalRules = defineModel<TraversalRules[]>("traversalRules", {
  required: true,
});
</script>

<template>
  <v-row class="text-textbody1 text-subtitle-2 pl-1">
    <v-col cols="4">Traversal Rules:</v-col>
    <v-col>
      <v-checkbox
        v-model="traversalRules"
        :value="TraversalRules.Children"
        density="compact"
        hide-details
        label="Children"
      />
      <v-checkbox
        v-model="traversalRules"
        :value="TraversalRules.Parents"
        density="compact"
        hide-details
        label="Parents"
      />
      <v-checkbox
        v-model="traversalRules"
        :value="TraversalRules.Predecessors"
        density="compact"
        hide-details
        label="Predecessors"
      />
      <v-checkbox
        v-model="traversalRules"
        :value="TraversalRules.Successors"
        density="compact"
        hide-details
        label="Successors"
      />
    </v-col>
  </v-row>
</template>

<style lang="scss" scoped></style>
