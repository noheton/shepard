<script setup lang="ts">
defineProps<{ text: string; icon: string }>();
const emit = defineEmits(["click"]);
</script>

<template>
  <v-btn
    class="text-body-1 pr-0"
    :text="text"
    variant="text"
    color="primary"
    density="compact"
    :prepend-icon="icon"
    @click.prevent.stop="() => emit('click')"
  />
</template>
