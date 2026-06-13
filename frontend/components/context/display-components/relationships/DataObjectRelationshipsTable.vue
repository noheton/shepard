<script lang="ts" setup>
import { compareNullableStrings } from "./compareNullableStrings";
import type { RelatedEntity } from "./relatedEntity";
import type { RelationshipTableElement } from "./relationshipTableElement";
import { mapRelatedEntityToRelationshipTableElement } from "./relationshipTableElementMappingUtil";
import { handleDataObjectUpdate } from "~/utils/resourceUpdateBus";

interface DataObjectRelationshipsTable {
  collectionId: number;
  dataObjectId: number;
  /**
   * V2-LINKS: the UUID-v7 appId of the collection this table is rendered for
   * (= the current route param). Used to build appId-keyed navigation routes
   * to sibling / predecessor / successor DataObjects — the numeric route 404s.
   */
  collectionAppId?: string;
  relatedEntities: RelatedEntity[];
  isAllowedToEditCollection: boolean;
  /**
   * PROV1k — optional map of predecessor numeric id → PROV-O / FAIR²R
   * relationship type. When provided, the typed relationship chip is shown
   * next to the "Predecessor" label in the Relationship column.
   * Omit (undefined) for backward-compat with callers that don't yet have
   * typed predecessor info.
   */
  predecessorRelationshipTypes?: Map<number, string>;
}

const props = defineProps<DataObjectRelationshipsTable>();

const selectedReferenceAppId = ref<string>("");
const selectedReferenceKind = ref<string>("DataObjectReference");
const selectedTableElement = ref<RelationshipTableElement | undefined>();
const showAddAnnotationDialog = ref(false);
const showDeleteRelationshipDialog = ref<boolean>(false);

// REF-EDIT-6 — URI reference edit dialog state
const showEditUriDialog = ref(false);
const editUriAppId = ref<string>("");
const editUriInitialName = ref<string>("");
const editUriInitialUri = ref<string>("");
const editUriInitialRelationship = ref<string | undefined>(undefined);

function openAddAnnotationDialog(relationshipElementId: number) {
  const relationShipElement = getRelationshipElementById(relationshipElementId);
  if (!relationShipElement) return;

  switch (relationShipElement.information.type.type) {
    case "Link":
    case "Collection Reference":
    case "Data Object Reference":
      selectedReferenceAppId.value =
        relationShipElement.information.referenceAppId ?? "";
      selectedReferenceKind.value =
        relationShipElement.information.referenceKind ?? "DataObjectReference";
      break;
    default:
      throw new Error("Unsupported relationship type");
  }
  showAddAnnotationDialog.value = true;
}

// REF-EDIT-6: open the URI reference edit dialog
function openEditUriDialog(relationshipElementId: number) {
  const el = getRelationshipElementById(relationshipElementId);
  if (!el) return;
  const { uriRefAppId, uriRefEditData } = el.actions;
  if (!uriRefAppId || !uriRefEditData) return;
  editUriAppId.value = uriRefAppId;
  editUriInitialName.value = uriRefEditData.name;
  editUriInitialUri.value = uriRefEditData.uri;
  editUriInitialRelationship.value = uriRefEditData.relationship;
  showEditUriDialog.value = true;
}

// REF-EDIT-6: refresh the relationships list after a successful URI edit
function onUriReferenceSaved() {
  handleDataObjectUpdate();
}

function openDeleteRelationshipDialog(relationshipElementId: number) {
  const relationShipElement = getRelationshipElementById(relationshipElementId);
  if (!relationShipElement) return;

  switch (relationShipElement.information.type.type) {
    case "Link":
      selectedTableElement.value = relationShipElement;
      break;
    case "Collection Reference":
      selectedTableElement.value = relationShipElement;
      break;
    case "Data Object Reference":
      selectedTableElement.value = relationShipElement;
      break;
    case "Data Object":
      selectedTableElement.value = relationShipElement;
      break;
  }
  showDeleteRelationshipDialog.value = true;
}

function getRelationshipElementById(
  id: number,
): RelationshipTableElement | undefined {
  return tableItems.value.find(element => element.id === id);
}

const tableItems = computed(() =>
  props.relatedEntities.map(entity => {
    const item = mapRelatedEntityToRelationshipTableElement(
      entity,
      props.collectionAppId,
    );
    // PROV1k: attach typed predecessor relationship type when available.
    if (item.relationship === "Predecessor" && props.predecessorRelationshipTypes) {
      item.predecessorRelationshipType = props.predecessorRelationshipTypes.get(entity.id);
    }
    return item;
  }),
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
  <div v-else style="overflow-x: auto">
  <DataTable :headers="headers" :items="tableItems">
    <template
      #[`item.relationship`]="{
        value,
        item,
      }: {
        value: RelationshipTableElement['relationship'];
        item: RelationshipTableElement;
      }"
    >
      <div class="d-flex align-center">
        <DataObjectRelationshipsRelationshipCell :value="value" />
        <!-- PROV1k: show typed relationship chip for predecessors -->
        <PredecessorRelationshipTypeChip
          v-if="item.predecessorRelationshipType"
          :relationship-type="item.predecessorRelationshipType"
        />
      </div>
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
        v-if="value.annotatable && value.referenceAppId"
        :can-delete="isAllowedToEditCollection"
        :annotated="
          new AnnotatedReference(
            value.referenceAppId,
            value.referenceKind ?? 'DataObjectReference',
          )
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
      <ActionContainer>
        <!-- REF-EDIT-6: edit button for URI references only -->
        <ActionButton
          v-if="isAllowedToEditCollection && !!value.uriRefAppId"
          class="relationship-actions"
          icon="mdi-pencil-outline"
          @click="() => openEditUriDialog(value.elementId)"
        />
        <ActionButton
          v-if="isAllowedToEditCollection && value.annotatable"
          class="relationship-actions"
          icon="mdi-tag-outline"
          @click="() => openAddAnnotationDialog(value.elementId)"
        />
        <ActionButton
          v-if="isAllowedToEditCollection"
          class="relationship-actions"
          icon="mdi-delete-outline"
          @click="() => openDeleteRelationshipDialog(value.elementId)"
        />
      </ActionContainer>
    </template>
    <template #bottom>
      <div class="bottom-border" />
    </template>
  </DataTable>
  </div>

  <AddAnnotationDialog
    v-if="showAddAnnotationDialog && selectedReferenceAppId"
    v-model:show-dialog="showAddAnnotationDialog"
    :annotated="
      new AnnotatedReference(selectedReferenceAppId, selectedReferenceKind)
    "
  />

  <DeleteRelationshipDialog
    v-model:show-dialog="showDeleteRelationshipDialog"
    :collection-id="props.collectionId"
    :data-object-id="props.dataObjectId"
    :table-element="selectedTableElement"
  />

  <!-- REF-EDIT-6: URI reference edit dialog -->
  <EditUriReferenceDialog
    v-if="showEditUriDialog"
    v-model:show-dialog="showEditUriDialog"
    :app-id="editUriAppId"
    :initial-name="editUriInitialName"
    :initial-uri="editUriInitialUri"
    :initial-relationship="editUriInitialRelationship"
    @saved="onUriReferenceSaved"
  />
</template>

<style lang="scss" scoped>
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
