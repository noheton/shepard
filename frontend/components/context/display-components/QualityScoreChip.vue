<script setup lang="ts">
/**
 * AI1c — displays the background-computed quality score for a timeseries
 * reference as a coloured Vuetify chip.
 *
 * - Green  (success)  ≥ 0.8 — high quality
 * - Amber  (warning)  ≥ 0.5 — medium quality
 * - Red    (error)    < 0.5 — low quality
 * - Dash   (—)       null   — not yet scored
 */
import { qualityScoreColor, qualityScoreLabel } from "~/utils/qualityScore";

const props = defineProps<{
  /** AI1c quality score in [0.0, 1.0], or null/undefined if not yet scored. */
  score: number | null | undefined;
}>();

const color = computed(() => qualityScoreColor(props.score));
const label = computed(() => qualityScoreLabel(props.score));
</script>

<template>
  <v-tooltip
    :text="label != null ? `Quality score: ${label}` : 'Not yet scored'"
    location="bottom"
  >
    <template #activator="{ props: tooltipProps }">
      <v-chip
        v-if="color != null && label != null"
        v-bind="tooltipProps"
        :color="color"
        size="small"
        variant="tonal"
        prepend-icon="mdi-star-circle-outline"
        label
      >
        {{ label }}
      </v-chip>
      <span
        v-else
        v-bind="tooltipProps"
        class="text-medium-emphasis text-body-2"
        style="cursor: default"
      >—</span>
    </template>
  </v-tooltip>
</template>
