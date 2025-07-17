<script setup lang="ts">
defineProps<{
  createdAt: Date;
  createdBy: string;
  biggerFont?: boolean;
}>();
</script>

<template>
  <div>
    {{ toShortDateString(createdAt) }}
  </div>
  <div
    class="text-textbody2 text-body-2"
    :style="{
      fontSize: biggerFont ? '' : '0.75rem !important',
      lineHeight: '16px',
    }"
  >
    by {{ createdBy }}
  </div>
</template>
