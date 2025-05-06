<script setup lang="ts">
import { useClipboard } from "@vueuse/core";

const { copy } = useClipboard();

const { text } = defineProps<{
  text?: string;
}>();

const copyText = () => {
  if (text) {
    copy(text);
    emitSuccess(`Copied "${text}"`);
  }
};
</script>

<template>
  <div v-if="!!text" class="text">
    {{ text }}
    <v-btn
      class="text-copy-icn"
      icon="mdi-content-copy"
      density="compact"
      variant="text"
      color="medium-emphasis"
      @click="copyText"
    />
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
