<script setup lang="ts">
defineSlots();

const props = defineProps({
  itemsPerPage: {
    type: Number,
    required: false,
    default: -1,
  },
  itemsForPagination: {
    type: Array,
    required: false,
    default: () => [],
  },
});

const page = ref(1);

const paginatedItems = computed(() => {
  if (page.value && props.itemsForPagination.length) {
    const start = (page.value - 1) * props.itemsPerPage;
    const end = start + props.itemsPerPage;
    return props.itemsForPagination.slice(start, end);
  }
  return [];
});
</script>

<template>
  <v-data-table
    sort-desc-icon="mdi-triangle-small-down"
    sort-asc-icon="mdi-triangle-small-up"
    :items="paginatedItems"
    :items-per-page="itemsPerPage"
  >
    <template v-for="(_, slot) of $slots" #[slot]="scope">
      <slot :name="slot" v-bind="scope" />
    </template>
    <template v-if="itemsForPagination.length > 0" #bottom>
      <v-divider :thickness="8" color="divider2" opacity="1" />
      <v-pagination
        v-model="page"
        :length="Math.ceil(itemsForPagination.length / itemsPerPage)"
        :total-visible="6"
      />
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
