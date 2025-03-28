<script setup lang="ts">
import DataObjectDataMetaCell from "./DataObjectDataMetaCell.vue";
import type { DataReference } from "./dataReference";
import type { DataTableElement } from "./dataTableElement";
import { mapDataReferenceToDataTableElement } from "./dataTableElementMappingUtil";

interface DataObjectDataReferencesTableProps {
  collectionId: number;
  dataObjectId: number;
  dataReferences: Array<DataReference>;
  isAllowedToEditCollection: boolean;
}
const props = defineProps<DataObjectDataReferencesTableProps>();
const router = useRouter();

const selectedReferenceId = ref<number>(0);
const showAddAnnotationDialog = ref(false);

function openAddAnnotationDialog(dataTableElementId: number) {
  selectedReferenceId.value = dataTableElementId;
  showAddAnnotationDialog.value = true;
}

function showDetails(pathFragment: string, id: number) {
  const route =
    collectionsPath +
    props.collectionId +
    dataObjectsPathFragment +
    props.dataObjectId +
    pathFragment +
    id;
  router.push(route);
}

const tableItems: Array<DataTableElement> = props.dataReferences.map(
  mapDataReferenceToDataTableElement,
);

const headers = [
  {
    title: "Type",
    value: "type",
    sort: (a: string, b: string) => a.localeCompare(b),
  },
  {
    title: "Name",
    value: "name",
    sort: (a: string, b: string) => a.localeCompare(b),
  },
  {
    title: "Meta",
    value: "meta",
    sort: (a: DataTableElement["meta"], b: DataTableElement["meta"]) =>
      a.id - b.id,
  },
  {
    title: "Created",
    value: "created",
    sort: (a: DataTableElement["created"], b: DataTableElement["created"]) =>
      a.createdAt.valueOf() - b.createdAt.valueOf(),
  },
  {
    title: "",
    value: "actions",
  },
];

const page = ref<number>(1);
const itemsPerPage = 10;

const pageCount = Math.ceil(tableItems.length / itemsPerPage);
</script>

<template>
  <EmptyListIcon v-if="tableItems.length === 0" label="No data yet" />
  <DataTable
    v-else
    v-model:page="page"
    :headers="headers"
    :items="tableItems"
    :items-per-page="itemsPerPage"
    hover
  >
    <template #[`item.meta`]="{ value }: { value: DataTableElement['meta'] }">
      <DataObjectDataMetaCell :meta="value" />
    </template>
    <template
      #[`item.created`]="{ value }: { value: DataTableElement['created'] }"
    >
      <CreatedTableCell
        :created-at="value.createdAt"
        :created-by="value.createdBy"
      />
    </template>
    <template
      #[`item.actions`]="{ value }: { value: DataTableElement['actions'] }"
    >
      <div class="data-table-row-actions d-flex ga-2">
        <v-btn
          v-if="value.showDetails.enabled"
          icon="mdi-eye-outline"
          density="compact"
          variant="flat"
          @click="
            () => showDetails(value.showDetails.pathFragment, value.elementId)
          "
        />
        <v-btn
          v-if="isAllowedToEditCollection"
          icon="mdi-tag-outline"
          density="compact"
          variant="flat"
          @click="() => openAddAnnotationDialog(value.elementId)"
        />
      </div>
    </template>
    <template #bottom>
      <v-divider :thickness="8" color="divider2" opacity="1" />
      <v-pagination v-model="page" :length="pageCount" />
    </template>
  </DataTable>

  <AddAnnotationDialog
    v-if="showAddAnnotationDialog"
    v-model:show-dialog="showAddAnnotationDialog"
    :collection-id="props.collectionId"
    :data-object-id="props.dataObjectId"
    :reference-id="selectedReferenceId"
  />
</template>

<style scoped lang="scss">
.v-table {
  :deep(thead) > tr > th {
    background-color: rgb(var(--v-theme-divider2));
  }
}

tr .data-table-row-actions {
  visibility: hidden;
}

tr:hover .data-table-row-actions {
  visibility: visible;
}
</style>
