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

const allTableItems = computed(() =>
  props.dataReferences.map(mapDataReferenceToDataTableElement),
);

// Per-kind filter chips above the table. "All" is the default; clicking a
// kind narrows the rows. Counts come from the un-filtered list so empty
// kinds still show (greyed) and users learn the data shape at a glance.
type RefKind = DataTableElement["type"];
const allKind = "All" as const;
type SelectedKind = typeof allKind | RefKind;
const selectedKind = ref<SelectedKind>(allKind);

const kindCounts = computed<Record<RefKind, number>>(() => {
  const counts: Record<RefKind, number> = {
    TimeSeries: 0,
    "Structured Data": 0,
    "File Bundle": 0,
  };
  for (const item of allTableItems.value) counts[item.type]++;
  return counts;
});

const tableItems = computed(() =>
  selectedKind.value === allKind
    ? allTableItems.value
    : allTableItems.value.filter(item => item.type === selectedKind.value),
);

const kindIcons: Record<RefKind, string> = {
  TimeSeries: "mdi-chart-line",
  "Structured Data": "mdi-code-json",
  "File Bundle": "mdi-file-multiple-outline",
};
const KIND_ORDER: RefKind[] = ["TimeSeries", "Structured Data", "File Bundle"];

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
  <!-- Kind summary + filter chips. Always rendered (even when empty) so the
       data shape is communicated at a glance. -->
  <div class="d-flex flex-wrap align-center ga-2 pb-3">
    <v-chip
      :variant="selectedKind === 'All' ? 'flat' : 'tonal'"
      :color="selectedKind === 'All' ? 'primary' : undefined"
      size="small"
      @click="selectedKind = 'All'"
    >
      All ({{ allTableItems.length }})
    </v-chip>
    <v-chip
      v-for="kind in KIND_ORDER"
      :key="kind"
      :variant="selectedKind === kind ? 'flat' : 'tonal'"
      :color="selectedKind === kind ? 'primary' : undefined"
      :disabled="kindCounts[kind] === 0"
      size="small"
      :prepend-icon="kindIcons[kind]"
      @click="selectedKind = kind"
    >
      {{ kind }} ({{ kindCounts[kind] }})
    </v-chip>
  </div>

  <EmptyListIcon v-if="tableItems.length === 0" label="No data yet" />
  <div v-else style="overflow-x: auto">
  <DataTable
    :headers="headers"
    :items-for-pagination="tableItems"
    :items-per-page="itemsPerPage"
  >
    <!--
      User feedback 2026-05-18: the eye-icon on hover was the only path
      to drill in. Title + Type are now clickable links — the eye-icon
      lives on as a tertiary action that mirrors the navigation, useful
      for keyboard / accessible users who don't trigger row-hover.
    -->
    <template #[`item.type`]="{ item }: { item: DataTableElement }">
      <a
        v-if="item.actions.showDetails.enabled"
        href="#"
        class="reference-link d-inline-flex align-center"
        @click.prevent="showDetails(item.actions.showDetails.pathFragment, item.actions.elementId)"
      >
        <v-icon :icon="kindIcons[item.type]" size="small" class="me-1" />
        {{ item.type }}
      </a>
      <span v-else class="d-inline-flex align-center">
        <v-icon :icon="kindIcons[item.type]" size="small" class="me-1" />
        {{ item.type }}
      </span>
    </template>
    <template #[`item.name`]="{ item }: { item: DataTableElement }">
      <a
        v-if="item.actions.showDetails.enabled"
        href="#"
        class="reference-link"
        @click.prevent="showDetails(item.actions.showDetails.pathFragment, item.actions.elementId)"
      >
        {{ item.name }}
      </a>
      <span v-else>{{ item.name }}</span>
    </template>
    <template #[`item.meta`]="{ value }: { value: DataTableElement['meta'] }">
      <DataObjectDataMetaCell :meta="value" />
      <SemanticAnnotationList
        :key="value.id"
        :can-delete="isAllowedToEditCollection"
        :limit="4"
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
  </div>

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

<style scoped>
.reference-link {
  color: rgb(var(--v-theme-primary));
  text-decoration: none;
  cursor: pointer;
}
.reference-link:hover {
  text-decoration: underline;
}
</style>
