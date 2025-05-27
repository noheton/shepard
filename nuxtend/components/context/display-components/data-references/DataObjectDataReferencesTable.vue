<script lang="ts" setup>
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

const tableItems = computed(() =>
  props.dataReferences.map(mapDataReferenceToDataTableElement),
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

const itemsPerPage = 10;
</script>

<template>
  <EmptyListIcon v-if="tableItems.length === 0" label="No data yet" />
  <DataTable
    v-else
    :headers="headers"
    :items-for-pagination="tableItems"
    :items-per-page="itemsPerPage"
    hover
  >
    <template #[`item.meta`]="{ value }: { value: DataTableElement['meta'] }">
      <DataObjectDataMetaCell :meta="value" />
      <SemanticAnnotationList
        :key="value.id"
        :can-delete="isAllowedToEditCollection"
        :annotated="
          new AnnotatedReference(collectionId, dataObjectId, value.id)
        "
      />
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
      <ActionContainer>
        <ActionButton
          v-if="value.showDetails.enabled"
          icon="mdi-eye-outline"
          @click="
            () => showDetails(value.showDetails.pathFragment, value.elementId)
          "
        />
        <ActionButton
          v-if="isAllowedToEditCollection"
          icon="mdi-tag-outline"
          @click="() => openAddAnnotationDialog(value.elementId)"
        />
      </ActionContainer>
    </template>
  </DataTable>

  <AddAnnotationDialog
    v-if="showAddAnnotationDialog"
    v-model:show-dialog="showAddAnnotationDialog"
    :annotated="
      new AnnotatedReference(
        props.collectionId,
        props.dataObjectId,
        selectedReferenceId,
      )
    "
  />
</template>
