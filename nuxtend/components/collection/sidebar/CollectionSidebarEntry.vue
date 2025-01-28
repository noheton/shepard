<script lang="ts" setup>
interface SidebarEntry {
  isFocused: boolean;
  title: string;
  to: string;
}
const props = defineProps<SidebarEntry>();
</script>

<template>
  <NuxtLink :to="props.to" class="link-button">
    <span class="link-text">
      {{ props.title }}
    </span>
  </NuxtLink>
</template>

<style lang="scss" scoped>
.link-button {
  color: rgb(var(--v-theme-textbody1));
  justify-content: left;
  display: inline-flex;
  min-height: 40px;
  width: 100%;
  text-decoration: none;
}
.link-text {
  align-content: center;
}
</style>
