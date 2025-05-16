<script setup lang="ts">
const { text } = defineProps<{
  text?: string;
}>();
</script>

<template>
  <div v-if="!!text" class="text">
    {{ text }}
    <ClipboardButton :text="text" class="text-copy-icn" />
  </div>
</template>

<style scoped lang="scss">
.text {
  width: fit-content;
  .text-copy-icn {
    visibility: hidden;
  }
  &:hover .text-copy-icn {
    visibility: visible;
  }
}
</style>
