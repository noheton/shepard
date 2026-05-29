<script lang="ts" setup>
/**
 * UI21-SIZEBAR-DATA — per-row cardinality bar shown in the container list.
 *
 * Lazily fetches the summary endpoint for this container and renders a
 * proportional bar relative to `maxCardinality` (the max across the visible
 * page, supplied by the parent ContainerList).  The bar is purely decorative —
 * if the fetch fails or the kind has no summary endpoint, nothing is shown.
 */
import type { ContainerType } from "@dlr-shepard/backend-client";
import { useContainerCardinalitySummary } from "~/composables/containers/useContainerCardinalitySummary";

const props = defineProps<{
  containerId: number;
  containerType: ContainerType;
  /** Maximum cardinality in the visible page — used to scale bar width. */
  maxCardinality: number;
}>();

const { summary } = useContainerCardinalitySummary(
  props.containerId,
  props.containerType,
);

/** Bar width as a percentage of the column, clamped between 2% and 100%. */
const barWidth = computed(() => {
  if (!summary.value || props.maxCardinality <= 0) return 0;
  const pct = (summary.value.cardinality / props.maxCardinality) * 100;
  return Math.max(2, Math.min(100, pct));
});

/** Human-readable label: "7 channels", "3 files", "12 payloads". */
const label = computed(() => {
  if (!summary.value) return "";
  const n = summary.value.cardinality;
  if (props.containerType === "TIMESERIES")
    return `${n} channel${n !== 1 ? "s" : ""}`;
  if (props.containerType === "FILE")
    return `${n} file${n !== 1 ? "s" : ""}`;
  if (props.containerType === "STRUCTUREDDATA")
    return `${n} payload${n !== 1 ? "s" : ""}`;
  return `${n}`;
});
</script>

<template>
  <div v-if="summary" class="sizebar-cell" :title="label">
    <div class="sizebar-track">
      <div
        class="sizebar-fill bg-primary"
        :style="{ width: barWidth + '%' }"
      />
    </div>
    <span class="sizebar-label text-caption text-textbody2">{{ label }}</span>
  </div>
  <div v-else class="sizebar-empty" />
</template>

<style lang="scss" scoped>
.sizebar-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: 100%;
  min-width: 80px;
}

.sizebar-track {
  height: 6px;
  border-radius: 3px;
  background: rgba(var(--v-theme-textbody2), 0.12);
  overflow: hidden;
  width: 100%;
}

.sizebar-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.3s ease;
  opacity: 0.7;
}

.sizebar-label {
  font-size: 10px;
  line-height: 1.2;
  white-space: nowrap;
}

.sizebar-empty {
  height: 22px;
}
</style>
