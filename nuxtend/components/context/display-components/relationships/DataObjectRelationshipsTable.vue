<script setup lang="ts">
import { compareNullableStrings } from "./compareNullableStrings";
import type { RelatedEntity } from "./relatedEntity";
import type { RelationshipTableElement } from "./relationshipTableElement";
import { mapRelatedEntityToRelationshipTableElement } from "./relationshipTableElementMappingUtil";

interface DataObjectRelationshipsTable {
  collectionId: number;
  dataObjectId: number;
  relatedEntities: RelatedEntity[];
}
const props = defineProps<DataObjectRelationshipsTable>();

const selectedDataObjectId = ref<number | undefined>(props.dataObjectId);
const selectedReferenceId = ref<number | undefined>(0);
const showAddAnnotationDialog = ref(false);

function openAddAnnotationDialog(relationshipElementId: number) {
  // before we can open the dialog, we have to check the relationship type
  const relationShipElement = getRelationshipElementById(relationshipElementId);
  if (!relationShipElement) return;

  switch (relationShipElement.type.type) {
    case "Data Object":
      selectedDataObjectId.value = relationShipElement.id;
      selectedReferenceId.value = undefined;
      break;
    case "Link":
      selectedDataObjectId.value = props.dataObjectId;
      selectedReferenceId.value = relationShipElement.id;
      break;
    case "Collection Reference":
      selectedDataObjectId.value = props.dataObjectId;
      selectedReferenceId.value = relationShipElement.id;
      break;
    case "Data Object Reference":
      selectedDataObjectId.value = props.dataObjectId;
      selectedReferenceId.value = relationShipElement.id;
      break;
  }

  showAddAnnotationDialog.value = true;
}

function getRelationshipElementById(
  id: number,
): RelationshipTableElement | undefined {
  return tableItems.find(element => element.id === id);
}

const tableItems: RelationshipTableElement[] = props.relatedEntities.map(
  mapRelatedEntityToRelationshipTableElement,
);

const headers = [
  {
    title: "Relationship",
    value: "relationship",
    sort: compareNullableStrings,
  },
  {
    title: "Name",
    value: "name",
    sort: (
      a: RelationshipTableElement["name"],
      b: RelationshipTableElement["name"],
    ) => a.value.localeCompare(b.value),
  },
  {
    title: "Type",
    value: "type",
    sort: (
      a: RelationshipTableElement["type"],
      b: RelationshipTableElement["type"],
    ) => a.type.localeCompare(b.type),
  },
  {
    title: "Created",
    value: "created",
    sort: (
      a: RelationshipTableElement["created"],
      b: RelationshipTableElement["created"],
    ) => a.createdAt.valueOf() - b.createdAt.valueOf(),
  },
  {
    title: "",
    value: "id",
  },
];
</script>

<template>
  <EmptyListIcon v-if="tableItems.length === 0" label="No relationships yet" />
  <DataTable v-else items-per-page="-1" :items="tableItems" :headers="headers">
    <template
      #[`item.relationship`]="{
        value,
      }: {
        value: RelationshipTableElement['relationship'];
      }"
    >
      <DataObjectRelationshipsRelationshipCell :value="value" />
    </template>
    <template
      #[`item.name`]="{ value }: { value: RelationshipTableElement['name'] }"
    >
      <NuxtLink :to="value.path">{{ value.value }}</NuxtLink>
    </template>
    <template
      #[`item.type`]="{ value }: { value: RelationshipTableElement['type'] }"
    >
      <TypeCell :value="value" />
    </template>
    <template
      #[`item.created`]="{
        value,
      }: {
        value: RelationshipTableElement['created'];
      }"
    >
      <CreatedTableCell
        :created-at="value.createdAt"
        :created-by="value.createdBy"
      />
    </template>
    <template
      #[`item.id`]="{ value }: { value: RelationshipTableElement['id'] }"
    >
      <v-btn
        icon="mdi-tag-outline"
        density="compact"
        variant="flat"
        @click="() => openAddAnnotationDialog(value)"
      />
    </template>
    <template #bottom>
      <div class="bottom-border" />
    </template>
  </DataTable>

  <AddAnnotationDialog
    v-if="showAddAnnotationDialog"
    v-model:show-dialog="showAddAnnotationDialog"
    :collection-id="props.collectionId"
    :data-object-id="selectedDataObjectId"
    :reference-id="selectedReferenceId"
  />
</template>

<style scoped lang="scss">
.v-table {
  :deep(tbody) > tr > td:first-of-type {
    background-color: rgb(var(--v-theme-divider2));
  }
}

.v-table {
  :deep(.bottom-border) {
    transition: inherit;
    border-bottom: thin solid
      rgba(var(--v-border-color), var(--v-border-opacity));
  }
}
</style>
