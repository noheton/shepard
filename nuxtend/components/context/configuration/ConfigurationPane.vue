<script setup lang="ts">
defineProps<{
  title: string;
  addButtonText: string;
  showTooltip?: boolean;
}>();
defineEmits<{
  (e: "show-create-dialog"): void;
}>();
</script>
<template>
  <div class="d-flex align-center ga-4">
    <h4 class="text-h4">{{ title }}</h4>
    <Tooltip v-if="showTooltip">
      <slot name="tooltip-content" />
    </Tooltip>
  </div>

  <div class="d-flex justify-end pt-8">
    <v-btn
      class="bg-primary text-canvas"
      variant="flat"
      :style="{ marginTop: '3px' }"
      @click="$emit('show-create-dialog')"
    >
      <template #prepend>
        <v-icon icon="mdi-plus-circle" color="canvas" />
      </template>
      {{ addButtonText }}
    </v-btn>
    <slot name="create-dialog" />
  </div>

  <slot name="table" />
  <slot name="confirmation-dialog" />
</template>
