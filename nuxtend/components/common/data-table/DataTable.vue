<script setup lang="ts">
defineSlots();
</script>

<template>
  <v-data-table
    sort-desc-icon="mdi-triangle-small-down"
    sort-asc-icon="mdi-triangle-small-up"
  >
    <template v-for="(_, slot) of $slots" #[slot]="scope">
      <slot :name="slot" v-bind="scope" />
    </template>
  </v-data-table>
</template>

<style scoped lang="scss">
.v-table {
  background-color: unset;

  :deep(thead) > tr > th {
    background-color: rgb(var(--v-theme-divider2)) !important;
  }

  :deep(td) {
    padding: 8px 24px !important;
  }

  :deep(tr):hover {
    background-color: rgb(var(--v-theme-focus1));
  }

  :deep(th) {
    font-size: 16px;
    padding: 8px 24px !important;
  }

  :deep(.mdi) {
    margin-left: 0.2em;
  }
}
</style>
