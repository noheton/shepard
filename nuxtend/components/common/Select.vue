<script setup lang="ts">
defineSlots();
</script>

<template>
  <v-select
    :list-props="{
      density: 'compact',
      bgColor: 'canvas',
    }"
  >
    <template v-for="(_, slot) of $slots" #[slot]="scope">
      <slot :name="slot" v-bind="scope" />
    </template>
  </v-select>
</template>
