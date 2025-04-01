<script setup lang="ts">
import { compareNullableStrings } from "./compareNullableStrings";
import type { RelatedEntity } from "./relatedEntity";
import type { RelationshipTableElement } from "./relationshipTableElement";
import { mapRelatedEntityToRelationshipTableElement } from "./relationshipTableElementMappingUtil";

interface DataObjectRelationshipsTable {
  collectionId: number;
  dataObjectId: number;
  relatedEntities: RelatedEntity[];
  isAllowedToEditCollection: boolean;
}
const props = defineProps<DataObjectRelationshipsTable>();

const selectedReferenceId = ref<number | undefined>(0);
const showAddAnnotationDialog = ref(false);

function openAddAnnotationDialog(relationshipElementId: number) {
  const relationShipElement = getRelationshipElementById(relationshipElementId);
  if (!relationShipElement) return;

  switch (relationShipElement.information.type.type) {
    case "Link":
    case "Collection Reference":
    case "Data Object Reference":
      selectedReferenceId.value = relationShipElement.id;
      break;
    default:
      throw new Error("Unsupported relationship type");
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
    title: "Information",
    value: "information",
    sort: (
      a: RelationshipTableElement["information"]["type"],
      b: RelationshipTableElement["information"]["type"],
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
    value: "actions",
  },
];
</script>

<template>
  <EmptyListIcon v-if="tableItems.length === 0" label="No relationships yet" />
  <DataTable
    v-else
    items-per-page="-1"
    :items="tableItems"
    :headers="headers"
    hover
  >
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
      #[`item.information`]="{
        value,
      }: {
        value: RelationshipTableElement['information'];
      }"
    >
      <TypeCell :value="value.type" />
      <SemanticAnnotationList
        :annotated="
          new AnnotatedReference(collectionId, dataObjectId, value.referenceId)
        "
      />
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
      #[`item.actions`]="{
        value,
      }: {
        value: RelationshipTableElement['actions'];
      }"
    >
      <v-btn
        v-if="isAllowedToEditCollection && value.annotatable"
        class="relationship-actions"
        icon="mdi-tag-outline"
        density="compact"
        variant="flat"
        @click="() => openAddAnnotationDialog(value.elementId)"
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
    :data-object-id="props.dataObjectId"
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

tr .relationship-actions {
  visibility: hidden;
}

tr:hover .relationship-actions {
  visibility: visible;
}
</style>
