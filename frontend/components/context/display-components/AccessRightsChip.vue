<script setup lang="ts">
// LIC1: small colored chip rendered next to titles + on Collection list rows.
// Color encoding mirrors the controlled vocabulary in utils/spdxLicenses.ts:
//   OPEN       -> success (green)
//   RESTRICTED -> warning (amber)
//   CLOSED     -> error   (red)
//   EMBARGOED  -> info    (blue)
//
// Unknown / unmapped values fall back to a neutral default chip so the UI
// never breaks on a server-side value the frontend hasn't seen yet.
import { getAccessRightsOption } from "~/utils/spdxLicenses";

const props = defineProps<{ accessRights: string }>();

const option = computed(() => getAccessRightsOption(props.accessRights));
const color = computed(() => option.value?.color ?? "default");
const label = computed(() => option.value?.label ?? props.accessRights);
const description = computed(() => option.value?.description ?? "");
</script>

<template>
  <v-tooltip :text="description" location="bottom" :disabled="!description">
    <template #activator="{ props: tooltipProps }">
      <v-chip
        v-bind="tooltipProps"
        :color="color"
        size="small"
        variant="tonal"
        prepend-icon="mdi-shield-lock-outline"
        label
      >
        {{ label }}
      </v-chip>
    </template>
  </v-tooltip>
</template>
