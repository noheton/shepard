<script setup lang="ts">
defineProps({
  name: {
    type: String,
    required: true,
  },
  wordCount: {
    type: Number,
    default: 50,
  },
});
</script>

<template>
  <span v-if="name">
    <span v-if="name.length < wordCount">
      {{ name }}
    </span>
    <span v-else v-b-tooltip.hover :title="name">
      {{ name.substring(0, wordCount) + "... " }}
    </span>
  </span>
</template>
