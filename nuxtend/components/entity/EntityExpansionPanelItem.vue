<script setup lang="ts">
defineProps<{ title: string; count?: number }>();
</script>

<template>
  <v-expansion-panel class="pb-8 bg-canvas">
    <v-expansion-panel-title
      v-slot="slotProps"
      min-height="32"
      class="px-0 py-0"
      hide-actions
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
      <v-spacer />
      <div v-if="slotProps.expanded">
        <slot name="append" />
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
  padding-top: 16px;
}
:deep(.v-expansion-panel-text__wrapper) {
  padding-left: 30.5px;
}
</style>
