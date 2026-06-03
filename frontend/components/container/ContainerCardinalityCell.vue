<script setup lang="ts">
/**
 * UI21-SIZEBAR-DATA — per-row cardinality cell for the /containers list sizebar.
 *
 * Fetches the domain-meaningful count (channel count for TS, file count for FC,
 * entry count for SDC) and emits it so the parent can normalise the sizebar
 * across all rows on the page.
 *
 * Unsupported container types (BASIC, SPATIALDATA, HDF5, VIDEO) emit null.
 */
import {
  useContainerCardinality,
  cardinalityLabel,
} from "~/composables/containers/useContainerCardinality";

const props = defineProps<{
  containerId: number;
  containerType: string;
}>();

const emit = defineEmits<{
  (e: "cardinality-resolved", payload: { id: number; count: number | null }): void;
}>();

const { cardinality, isLoading } = useContainerCardinality(
  props.containerId,
  props.containerType,
);

watch(
  cardinality,
  v => {
    emit("cardinality-resolved", { id: props.containerId, count: v });
  },
  { immediate: true },
);

const label = computed(() =>
  cardinality.value !== null
    ? cardinalityLabel(props.containerType, cardinality.value)
    : null,
);
</script>

<template>
  <span v-if="isLoading" class="d-inline-flex align-center">
    <v-progress-circular
      indeterminate
      size="12"
      width="2"
      color="medium-emphasis"
      aria-label="Loading cardinality"
    />
  </span>
  <span
    v-else-if="label !== null"
    class="text-body-2 text-medium-emphasis"
    :title="label"
  >{{ label }}</span>
  <span v-else class="text-medium-emphasis">—</span>
</template>
