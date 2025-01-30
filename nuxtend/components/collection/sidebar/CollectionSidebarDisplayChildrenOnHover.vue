<script setup lang="ts">
defineProps<{ displayChildrenWithoutHover: boolean }>();
</script>

<template>
  <div :class="displayChildrenWithoutHover ? '' : 'display-on-hover'">
    <slot />
  </div>
</template>

<style lang="scss" scoped>
.display-on-hover {
  visibility: hidden;
}

.v-list-item:hover .display-on-hover {
  visibility: visible;
}

.v-card-item:hover .display-on-hover {
  visibility: visible !important;
}
</style>
