<script setup lang="ts">
const props = defineProps<{ title: string }>();
</script>

<template>
  <v-expansion-panel class="pb-4">
    <v-expansion-panel-title
      v-slot="slotProps"
      :expand-icon="() => null"
      :collapse-icon="() => null"
    >
      <v-icon
        :icon="slotProps.expanded ? 'mdi-chevron-down' : 'mdi-chevron-right'"
        class
      />
      <div class="text-subtitle-1 text-black-500 font-weight-medium pl-2">
        {{ props.title }}
      </div>
    </v-expansion-panel-title>
    <v-expansion-panel-text>
      <slot />
    </v-expansion-panel-text>
  </v-expansion-panel>
</template>
