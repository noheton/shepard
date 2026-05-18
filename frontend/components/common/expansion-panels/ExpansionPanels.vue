<script setup lang="ts">
const props = withDefaults(defineProps<{ defaultOpen?: number[] }>(), {
  defaultOpen: () => [],
});
const openPanels = ref<number[]>(props.defaultOpen);
</script>

<template>
  <v-expansion-panels v-model="openPanels" variant="accordion" multiple static tile flat>
    <slot />
  </v-expansion-panels>
</template>
