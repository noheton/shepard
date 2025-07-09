<script lang="ts" setup>
import { TraversalRules } from "@dlr-shepard/backend-client";

defineProps<{ collectionId: number }>();

const traversalRules = defineModel<TraversalRules[] | []>("traversalRules", {
  required: true,
});

const emit = defineEmits<{
  (e: "traversalRuleSelected", array: TraversalRules[]): void;
}>();

const onSelect = () => {
  emit("traversalRuleSelected", traversalRules.value);
};
</script>

<template>
  <v-row class="text-textbody1 text-subtitle-2 pl-1">
    <v-col cols="4">Traversal Rules:</v-col>
    <v-col>
      <v-checkbox
        v-model="traversalRules[0]"
        :value="TraversalRules.Children"
        density="compact"
        hide-details
        label="Children"
        @click:append="onSelect"
      />
      <v-checkbox
        v-model="traversalRules[1]"
        :value="TraversalRules.Parents"
        density="compact"
        hide-details
        label="Parents"
      />
      <v-checkbox
        v-model="traversalRules[2]"
        :value="TraversalRules.Predecessors"
        density="compact"
        hide-details
        label="Predecessors"
      />
      <v-checkbox
        v-model="traversalRules[3]"
        :value="TraversalRules.Successors"
        density="compact"
        hide-details
        label="Successors"
      />
    </v-col>
  </v-row>
</template>

<style lang="scss" scoped>
.v-col-3 {
  max-width: 19%;
}
</style>
