<script setup lang="ts">
defineSlots();
</script>

<template>
  <!--
    UIRULE-DROPDOWN-SEARCH-SORT: this shared wrapper is a v-autocomplete (not a
    bare v-select) so every call site gets search-as-you-type for free. Drop-in:
    same props / v-model / item-title / item-value / slots as v-select.
    auto-select-first lets a user type + Enter to pick the first filtered match.
    Callers are responsible for naturally ordering their :items (naturalSort).
  -->
  <v-autocomplete
    auto-select-first
    :list-props="{
      density: 'compact',
      bgColor: 'canvas',
    }"
  >
    <template v-for="(_, slot) of $slots" #[slot]="scope">
      <slot :name="slot" v-bind="scope" />
    </template>
  </v-autocomplete>
</template>
