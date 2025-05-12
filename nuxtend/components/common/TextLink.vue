<script setup lang="ts">
defineProps<{
  to: string;
  target?: string;
  text: string;
}>();
</script>

<template>
  <NuxtLink :to="to" :target="target">
    {{ text }}
  </NuxtLink>
</template>

<style lang="scss" scoped>
a {
  color: rgb(var(--v-theme-textbody));
  text-decoration: none;
}
a:hover {
  text-decoration: underline;
}
</style>
