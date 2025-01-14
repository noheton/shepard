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

<style>
.v-table > .v-table__wrapper > table > tbody > tr > td,
.v-table > .v-table__wrapper > table > tbody > tr > th {
  padding: 8px 24px !important;
}

.v-table > .v-table__wrapper > table > thead > tr > td,
.v-table > .v-table__wrapper > table > thead > tr > th {
  background-color: rgb(var(--v-theme-divider2));
  font-size: 16px;
  padding: 8px 24px !important;
}
</style>
