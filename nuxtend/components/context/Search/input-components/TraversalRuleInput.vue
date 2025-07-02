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
    <v-col cols="2">Traversal Rules:</v-col>
    <v-col cols="3">
      <v-container fluid>
        <ul>
          <li>
            <v-checkbox
              v-model="selectedTraversalRules[0]"
              label="Children"
              :value="TraversalRules.Children"
            />
          </li>
          <li>
            <v-checkbox
              v-model="selectedTraversalRules[1]"
              label="Parents"
              :value="TraversalRules.Parents"
            />
          </li>
          <li>
            <v-checkbox
              v-model="selectedTraversalRules[2]"
              label="Predecessors"
              :value="TraversalRules.Predecessors"
            />
          </li>
          <li>
            <v-checkbox
              v-model="selectedTraversalRules[3]"
              label="Successors"
              :value="TraversalRules.Successors"
            />
          </li>
        </ul>
      </v-container>
    </v-col>
  </v-row>
</template>

<style scoped lang="scss">
li {
  list-style-type: none;
}
</style>
