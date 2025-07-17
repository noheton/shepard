<script lang="ts" setup>
const router = useRouter();

export type DataTableHeader = { key: string };
export type HasUrl = { url: string };

const props = defineProps({
  itemsPerPage: {
    type: Number,
    required: false,
    default: -1,
  },
  itemsForPagination: {
    type: Array<HasUrl>,
    required: false,
    default: () => [],
  },
  headers: { type: Array<DataTableHeader>, required: true },
  newTab: { type: Boolean, required: false, default: false },
});

function transformHeaderForTable(header: DataTableHeader): string {
  return "item." + header.key;
}

function navigateTo(url: string): void {
  if (props.newTab) {
    const routeData = router.resolve(url);
    window.open(routeData.href);
  } else {
    router.push(url);
  }
}
</script>

<template>
  <DataTable
    :headers="headers"
    :items-for-pagination="itemsForPagination"
    :items-per-page="itemsPerPage"
  >
    <template #item="rowProps">
      <v-data-table-row
        v-bind="rowProps"
        @click="navigateTo(rowProps.item.url)"
      >
        <template v-for="header in headers" #[transformHeaderForTable(header)]>
          {{ rowProps.item[header.key] }}
        </template>
      </v-data-table-row>
    </template>
  </DataTable>
</template>

<style lang="scss" scoped></style>
