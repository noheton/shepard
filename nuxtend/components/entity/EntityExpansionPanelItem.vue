<script setup lang="ts">
defineProps<{ title: string; count?: number }>();
</script>

<template>
  <v-expansion-panel class="pb-4">
    <v-expansion-panel-title
      v-slot="slotProps"
      class="pl-0"
      :expand-icon="() => null"
      :collapse-icon="() => null"
    >
      <v-icon
        :icon="slotProps.expanded ? 'mdi-chevron-down' : 'mdi-chevron-right'"
        class
      />
      <div class="text-subtitle-1 text-black-500 pl-2">
        {{ title }}
      </div>
      <div
        v-if="count !== undefined && count !== null"
        class="text-subtitle-1 text-black-200 pl-2"
      >
        ({{ count }})
      </div>
    </v-expansion-panel-title>
    <v-expansion-panel-text>
      <slot />
    </v-expansion-panel-text>
  </v-expansion-panel>
</template>
