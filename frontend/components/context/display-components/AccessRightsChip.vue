<script setup lang="ts">
// LIC1: small colored chip rendered next to titles + on Collection list rows.
// Color encoding mirrors the controlled vocabulary in utils/spdxLicenses.ts:
//   OPEN       -> success (green)
//   RESTRICTED -> warning (amber)
//   CLOSED     -> error   (red)
//   EMBARGOED  -> info    (blue)
//
// When accessRights is null/undefined (collections that predate LIC1), renders
// a neutral "Not set" chip (UX-WALK-2026-05-29-08) so the column always shows
// a chip rather than a bare dash.
//
// Unknown / unmapped values fall back to a neutral default chip so the UI
// never breaks on a server-side value the frontend hasn't seen yet.
import { getAccessRightsOption } from "~/utils/spdxLicenses";

const props = defineProps<{ accessRights: string | null | undefined }>();

const isUnset = computed(() => !props.accessRights);
const option = computed(() => (props.accessRights ? getAccessRightsOption(props.accessRights) : undefined));
const color = computed(() => (isUnset.value ? "default" : (option.value?.color ?? "default")));
const label = computed(() => (isUnset.value ? "Not set" : (option.value?.label ?? props.accessRights)));
const icon = computed(() => (isUnset.value ? "mdi-help-circle-outline" : "mdi-shield-lock-outline"));
const description = computed(() => (isUnset.value ? "" : (option.value?.description ?? "")));
</script>

<template>
  <v-tooltip :text="description" location="bottom" :disabled="!description">
    <template #activator="{ props: tooltipProps }">
      <v-chip
        v-bind="tooltipProps"
        :color="color"
        size="small"
        variant="tonal"
        :prepend-icon="icon"
        label
      >
        {{ label }}
      </v-chip>
    </template>
  </v-tooltip>
</template>
