<script setup lang="ts">
/**
 * QM1a — DataObject lifecycle status badge.
 *
 * Standard lifecycle: DRAFT → IN_REVIEW → READY → PUBLISHED → ARCHIVED.
 * MFG1 / QM1a quality-engineering branch (role-gated on write):
 *   NCR_OPEN, ON_HOLD, REJECTED, CERTIFIED, CONCESSION_PENDING.
 *
 * Each entry provides:
 *   - color: a Vuetify colour token (or "default" / "orange")
 *   - label: human-readable display string
 *   - variant: optional override; QM1a uses `outlined` for REJECTED /
 *     CONCESSION_PENDING to visually separate "decision-pending" /
 *     "decision-no-go" from the filled "in-progress" states.
 *   - icon: optional prepended MDI glyph (NCR / disposition states).
 */
const STATUS_CONFIG: Record<
  string,
  { color: string; label: string; variant?: string; icon?: string }
> = {
  DRAFT: { color: "default", label: "Draft" },
  IN_REVIEW: { color: "warning", label: "In Review" },
  READY: { color: "success", label: "Ready" },
  PUBLISHED: { color: "primary", label: "Published" },
  ARCHIVED: { color: "secondary", label: "Archived" },
  // MFG1 / QM1a — EN 9100 quality-engineering statuses
  NCR_OPEN: { color: "error", label: "NCR Open", variant: "flat", icon: "mdi-alert-octagon" },
  ON_HOLD: { color: "orange", label: "On Hold" },
  REJECTED: { color: "error", label: "Rejected", variant: "outlined", icon: "mdi-close-circle-outline" },
  CERTIFIED: { color: "success", label: "Certified" },
  // QM1a — concession / "use-as-is" disposition pending
  CONCESSION_PENDING: {
    color: "warning",
    label: "Concession Pending",
    variant: "outlined",
    icon: "mdi-shield-alert-outline",
  },
};

const props = defineProps<{ status: string }>();

const config = computed(() => STATUS_CONFIG[props.status] ?? { color: "default", label: props.status });
const chipVariant = computed(() => (config.value.variant ?? "tonal") as "tonal" | "flat" | "outlined");
</script>

<template>
  <v-chip
    :color="config.color"
    :variant="chipVariant"
    :prepend-icon="config.icon"
    size="small"
    label
    data-testid="status-chip"
  >
    {{ config.label }}
  </v-chip>
</template>
