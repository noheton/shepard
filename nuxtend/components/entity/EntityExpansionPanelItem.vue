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
      <div class="text-h5 text-black-500 pl-2">
        {{ title }}
      </div>
      <div
        v-if="count !== undefined && count !== null"
        class="text-h5 text-black-200 pl-2"
      >
        ({{ count }})
      </div>
    </v-expansion-panel-title>
    <v-expansion-panel-text>
      <slot />
    </v-expansion-panel-text>
  </v-expansion-panel>
</template>
