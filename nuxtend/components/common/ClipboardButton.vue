<script setup lang="ts">
import { useClipboard } from "@vueuse/core";

const { copy } = useClipboard();

const { text, successMessage = null } = defineProps<{
  text: string;
  successMessage?: string;
}>();

const copyText = () => {
  copy(text);
  if (successMessage === null) {
    emitSuccess(`Copied "${text}"`);
  } else {
    emitSuccess(successMessage);
  }
};
</script>

<template>
  <v-btn
    icon="mdi-content-copy"
    density="compact"
    variant="text"
    color="medium-emphasis"
    @click="copyText"
  />
</template>

<style scoped lang="scss"></style>
