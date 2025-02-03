<script setup lang="ts">
defineProps<{ label: string }>();
</script>

<template>
  <div class="text-center">
    <v-img
      src="../../assets/empty_list.svg"
      height="77"
      width="77"
      class="mx-auto"
    />
    <span class="text-body-2 text-textbody2">{{ label }}</span>
  </div>
</template>
