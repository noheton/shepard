<script setup lang="ts">
import { TraversalRules } from "@dlr-shepard/backend-client";

defineProps<{ collectionId: number }>();

/*
const traversalRule = defineModel<number | undefined>("traversalRule", {
  required: true,
});
*/

const selectedTraversalRules = ref<TraversalRules[]>([]);

/*
const emit = defineEmits<{
  (e: "traversalRuleSelected", id: number): void;
}>();
*/
</script>

<template>
  <v-row class="text-textbody1 text-subtitle-2 pl-1">
    <v-col cols="3">Traversal Rules:</v-col>
    <v-col cols="3">
      <v-checkbox
        v-model="selectedTraversalRules[0]"
        density="compact"
        hide-details
        label="Children"
        :value="TraversalRules.Children"
      />
      <v-checkbox
        v-model="selectedTraversalRules[1]"
        density="compact"
        hide-details
        label="Parents"
        :value="TraversalRules.Parents"
      />
      <v-checkbox
        v-model="selectedTraversalRules[2]"
        density="compact"
        hide-details
        label="Predecessors"
        :value="TraversalRules.Predecessors"
      />
      <v-checkbox
        v-model="selectedTraversalRules[3]"
        density="compact"
        hide-details
        label="Successors"
        :value="TraversalRules.Successors"
      />
    </v-col>
  </v-row>
</template>

<style scoped lang="scss">
.v-col-3 {
  max-width: 19%;
}
</style>
