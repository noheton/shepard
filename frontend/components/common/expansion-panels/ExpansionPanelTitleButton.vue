<script setup lang="ts">
defineProps<{ text: string; icon: string }>();
const emit = defineEmits(["click"]);
</script>

<template>
  <v-btn
    class="text-body-1"
    :text="text"
    variant="text"
    color="primary"
    height="32px"
    density="compact"
    :prepend-icon="icon"
    @click.prevent.stop="() => emit('click')"
  />
</template>
