<script setup lang="ts">
defineProps<{ title: string; count?: number }>();
</script>

<template>
  <v-expansion-panel class="pb-8">
    <v-expansion-panel-title
      v-slot="slotProps"
      min-height="32"
      class="pl-0 py-0"
      :expand-icon="() => null"
      :collapse-icon="() => null"
    >
      <v-icon
        :icon="slotProps.expanded ? 'mdi-chevron-down' : 'mdi-chevron-right'"
        class
      />
      <div class="text-h5 text-textbody1 pl-2">
        {{ title }}
      </div>
      <div
        v-if="count !== undefined && count !== null"
        class="text-h5 text-low-emphasis pl-2"
      >
        ({{ count }})
      </div>
    </v-expansion-panel-title>
    <v-expansion-panel-text class="no-padding-bottom">
      <slot />
    </v-expansion-panel-text>
  </v-expansion-panel>
</template>

<style scoped>
.no-padding-bottom ::v-deep .v-expansion-panel-text__wrapper {
  padding-bottom: 0;
}
</style>
