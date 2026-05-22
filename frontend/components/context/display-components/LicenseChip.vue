<script setup lang="ts">
// LIC1: chip rendered next to titles + on Collection list rows showing the
// SPDX license identifier. Title text is the expanded license name when the
// SPDX id is in our curated list; otherwise the raw string is displayed
// verbatim (operators can supply custom license expressions).
import { getSpdxLicense } from "~/utils/spdxLicenses";

const props = defineProps<{ license: string }>();

const expanded = computed(() => getSpdxLicense(props.license));
const tooltip = computed(() => expanded.value?.title ?? props.license);
const isProprietary = computed(() => props.license === "PROPRIETARY");
</script>

<template>
  <v-tooltip :text="tooltip" location="bottom">
    <template #activator="{ props: tooltipProps }">
      <v-chip
        v-bind="tooltipProps"
        :color="isProprietary ? 'error' : 'primary'"
        size="small"
        variant="tonal"
        prepend-icon="mdi-license"
        label
      >
        {{ license }}
      </v-chip>
    </template>
  </v-tooltip>
</template>
