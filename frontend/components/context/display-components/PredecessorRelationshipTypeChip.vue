<script setup lang="ts">
/**
 * PROV1k — compact chip that labels the relationship type of a predecessor link.
 *
 * Displays a coloured Vuetify chip for one of the PROV-O / FAIR²R
 * relationship types that PROV1k introduces:
 *   - prov:wasInformedBy   (default / generic — QM1b "normal")  — grey
 *   - prov:wasRevisionOf   (direct revision — QM1b "re-test")    — blue
 *   - fair2r:repairs        (rework / NCR fix — QM1b "rework")    — orange
 *   - fair2r:concession     (concession / use-as-is — QM1b)       — amber
 *
 * Renders nothing (null slot) when relationshipType is absent or unknown,
 * so callers with pre-PROV1k data are unaffected.
 */

/**
 * Configuration for each known relationship type.
 * The `label` is the short display text; `tooltip` gives the full IRI context.
 */
const RELATIONSHIP_CONFIG: Record<
  string,
  { color: string; label: string; tooltip: string }
> = {
  "prov:wasInformedBy": {
    color: "default",
    label: "informed by",
    tooltip:
      "prov:wasInformedBy — generic informational dependency between activities",
  },
  "prov:wasRevisionOf": {
    color: "blue",
    label: "revision of",
    tooltip:
      "prov:wasRevisionOf — this DataObject is a direct revision or correction of the predecessor",
  },
  "fair2r:repairs": {
    color: "orange",
    label: "repairs",
    tooltip:
      "fair2r:repairs — rework / NCR-repair relationship (e.g. after a non-conformance)",
  },
  "fair2r:concession": {
    color: "amber",
    label: "concession",
    tooltip:
      "fair2r:concession — the successor was accepted under a concession ('use-as-is') after the predecessor failed its acceptance criterion",
  },
};

const props = defineProps<{
  /** PROV-O / FAIR²R relationship type string, or null/undefined to hide the chip. */
  relationshipType?: string | null;
}>();

const config = computed(() => {
  if (!props.relationshipType) return null;
  return (
    RELATIONSHIP_CONFIG[props.relationshipType] ?? {
      color: "default",
      label: props.relationshipType,
      tooltip: props.relationshipType,
    }
  );
});
</script>

<template>
  <v-tooltip
    v-if="config"
    :text="config.tooltip"
    location="top"
    max-width="320"
  >
    <template #activator="{ props: tip }">
      <v-chip
        v-bind="tip"
        :color="config.color"
        size="x-small"
        variant="tonal"
        label
        class="ml-1"
        data-testid="predecessor-relationship-type-chip"
      >
        {{ config.label }}
      </v-chip>
    </template>
  </v-tooltip>
</template>
