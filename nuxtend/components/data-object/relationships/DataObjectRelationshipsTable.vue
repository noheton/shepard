<script setup lang="ts">
import { compareNullableStrings } from "./compareNullableStrings";
import DataObjectRelationshipsTypeCell from "./DataObjectRelationshipsTypeCell.vue";
import type { RelatedEntity } from "./relatedEntity";
import type { RelationshipTableElement } from "./relationshipTableElement";
import { mapRelatedEntityToRelationshipTableElement } from "./relationshipTableElementMappingUtil";

interface DataObjectRelationshipsTable {
  relatedEntities: RelatedEntity[];
}
const props = defineProps<DataObjectRelationshipsTable>();

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
    ) => a.value.localeCompare(b.value),
  },
  {
    title: "Created",
    value: "created",
    sort: (
      a: RelationshipTableElement["created"],
      b: RelationshipTableElement["created"],
    ) => a.createdAt.valueOf() - b.createdAt.valueOf(),
  },
];
</script>

<template>
  <CommonEmptyListIcon
    v-if="tableItems.length === 0"
    label="No relationships yet"
  />
  <CommonDataTable
    v-else
    items-per-page="-1"
    :items="tableItems"
    :headers="headers"
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
      #[`item.type`]="{ value }: { value: RelationshipTableElement['type'] }"
    >
      <DataObjectRelationshipsTypeCell :value="value" />
    </template>
    <template
      #[`item.created`]="{
        value,
      }: {
        value: RelationshipTableElement['created'];
      }"
    >
      <CommonDataTableCreatedCell
        :created-at="value.createdAt"
        :created-by="value.createdBy"
      />
    </template>
    <template #bottom>
      <div class="bottom-border" />
    </template>
  </CommonDataTable>
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
