<script setup lang="ts">
defineProps<{
  icon: string;
  color?: string;
}>();

const emit = defineEmits(["click"]);
</script>

<template>
  <v-btn
    class="data-table-actions"
    density="compact"
    :icon="icon"
    variant="text"
    :color="color ?? 'primary'"
    @click="emit('click')"
  />
</template>

<style scoped lang="scss">
tr .data-table-actions {
  visibility: hidden;
}

tr:hover .data-table-actions {
  visibility: visible;
}
</style>
