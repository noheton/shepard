<script setup lang="ts">
const STATUS_CONFIG: Record<string, { color: string; label: string }> = {
  DRAFT: { color: "default", label: "Draft" },
  IN_REVIEW: { color: "warning", label: "In Review" },
  READY: { color: "success", label: "Ready" },
  PUBLISHED: { color: "primary", label: "Published" },
  ARCHIVED: { color: "secondary", label: "Archived" },
  // MFG1 — EN 9100 quality-engineering statuses
  NCR_OPEN: { color: "error", label: "NCR Open" },
  ON_HOLD: { color: "orange", label: "On Hold" },
  REJECTED: { color: "error", label: "Rejected" },
  CERTIFIED: { color: "success", label: "Certified" },
};

const props = defineProps<{ status: string }>();

const config = computed(() => STATUS_CONFIG[props.status] ?? { color: "default", label: props.status });
</script>

<template>
  <v-chip :color="config.color" size="small" variant="tonal" label>
    {{ config.label }}
  </v-chip>
</template>
