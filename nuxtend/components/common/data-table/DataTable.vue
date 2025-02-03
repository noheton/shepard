<script setup lang="ts">
defineSlots();
</script>

<template>
  <v-data-table>
    <template v-for="(_, slot) of $slots" #[slot]="scope">
      <slot :name="slot" v-bind="scope" />
    </template>
  </v-data-table>
</template>

<style scoped lang="scss">
.v-table {
  background-color: unset;
  :deep(td) {
    padding: 8px 24px !important;
  }

  :deep(th) {
    font-size: 16px;
    padding: 8px 24px !important;
  }
}
</style>
