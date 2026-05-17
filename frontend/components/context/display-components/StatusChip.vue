<script setup lang="ts">
const STATUS_CONFIG: Record<string, { color: string; label: string }> = {
  DRAFT: { color: "default", label: "Draft" },
  IN_REVIEW: { color: "warning", label: "In Review" },
  READY: { color: "success", label: "Ready" },
  PUBLISHED: { color: "primary", label: "Published" },
  ARCHIVED: { color: "secondary", label: "Archived" },
};

const props = defineProps<{ status: string }>();

const config = computed(() => STATUS_CONFIG[props.status] ?? { color: "default", label: props.status });
</script>

<template>
  <v-chip :color="config.color" size="small" variant="tonal" label>
    {{ config.label }}
  </v-chip>
</template>
