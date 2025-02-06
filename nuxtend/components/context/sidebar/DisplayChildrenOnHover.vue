<script setup lang="ts">
defineProps<{ displayChildrenWithoutHover: boolean }>();
</script>

<template>
  <div
    style="
      height: 100%;
      display: flex;
      align-items: center;
      justify-content: center;
      align-content: center;
    "
    :class="displayChildrenWithoutHover ? '' : 'display-on-hover'"
  >
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
