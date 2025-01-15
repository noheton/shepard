<script lang="ts" setup>
interface SidebarHeader {
  isFocused: boolean;
  height?: string;
  to?: string;
}
const props = defineProps<SidebarHeader>();
</script>

<template>
  <v-card
    flat
    hover
    rounded="0"
    :height="props.height"
    :to="props.to"
    :color="
      props.isFocused
        ? 'rgb(var(--v-theme-focus1))'
        : 'rgb(var(--v-theme-treeview))'
    "
    class="d-flex ga-0"
  >
    <div
      class="card-focus-start"
      :style="{ visibility: isFocused ? 'visible' : 'hidden' }"
    />
    <v-card-item class="w-100">
      <slot />
    </v-card-item>
  </v-card>
</template>

<style lang="scss" scoped>
.card-focus-start {
  background-color: rgb(var(--v-theme-primary));
  min-width: 4px;
  max-width: 4px;
}
</style>
