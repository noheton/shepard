<script setup lang="ts">
// PlaceholderImplStatus — small status card showing backend implementation
// state + backlog row + design doc reference. Always visible (basic and
// advanced mode) so users always know "is this real yet, and where can I
// follow progress?".

type BackendStatus =
  | "shipped"
  | "partial"
  | "designed"
  | "queued"
  | "stub";

const props = defineProps<{
  backend: BackendStatus;
  backlogRow?: string;
  designDoc?: string;
  endpoint?: string | null;
  notes?: string;
}>();

const colorMap: Record<BackendStatus, string> = {
  shipped: "success",
  partial: "info",
  designed: "warning",
  queued: "grey",
  stub: "error",
};

const labelMap: Record<BackendStatus, string> = {
  shipped: "Backend shipped",
  partial: "Backend partial",
  designed: "Backend designed (not shipped)",
  queued: "Backend queued",
  stub: "Backend stub only",
};

const color = computed(() => colorMap[props.backend]);
const label = computed(() => labelMap[props.backend]);
</script>

<template>
  <v-card variant="outlined" class="mt-4">
    <v-card-text class="d-flex flex-column ga-2">
      <div class="d-flex align-center ga-2 flex-wrap">
        <v-chip :color="color" size="small" variant="tonal">
          <v-icon size="small" start>mdi-circle</v-icon>
          {{ label }}
        </v-chip>
        <v-chip v-if="backlogRow" size="small" variant="outlined">
          <v-icon size="small" start>mdi-tag-outline</v-icon>
          {{ backlogRow }}
        </v-chip>
        <v-chip v-if="endpoint" size="small" variant="outlined">
          <v-icon size="small" start>mdi-api</v-icon>
          <code class="text-caption">{{ endpoint }}</code>
        </v-chip>
      </div>
      <div v-if="designDoc" class="text-caption text-medium-emphasis">
        Design doc: <code>{{ designDoc }}</code>
      </div>
      <div v-if="notes" class="text-caption text-medium-emphasis">{{ notes }}</div>
    </v-card-text>
  </v-card>
</template>
