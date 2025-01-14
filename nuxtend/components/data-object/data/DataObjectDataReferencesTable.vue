<script setup lang="ts">
import {
  instanceOfFileReference,
  instanceOfTimeseriesReference,
  type FileReference,
  type StructuredDataReference,
  type TimeseriesReference,
} from "@dlr-shepard/backend-client";
import DataObjectDataMetaCell from "./DataObjectDataMetaCell.vue";

interface DataObjectDataReferencesTable {
  dataReferences: Array<DataReference>;
}
const props = defineProps<DataObjectDataReferencesTable>();

export type DataReference = (
  | TimeseriesReference
  | FileReference
  | StructuredDataReference
) & { referencedContainerName: string };

type TableElement = {
  type: "TimeSeries" | "Structured Data" | "File";
  name: string;
  meta: {
    id: number;
    containerId: number;
    containerName: string;
    interval?: string;
    fileCount?: number;
  };
  created: {
    createdBy: string;
    createdAt: Date;
  };
};

const mapRefType = (
  ref: DataReference,
): "TimeSeries" | "File" | "Structured Data" => {
  if (instanceOfTimeseriesReference(ref)) return "TimeSeries";
  if (instanceOfFileReference(ref)) return "File";
  return "Structured Data";
};

const mapContainerMetaData = (
  ref: DataReference,
): {
  containerId: number;
  containerName: string;
  interval?: string;
  fileCount?: number;
} => {
  if (instanceOfTimeseriesReference(ref)) {
    return {
      containerId: ref.timeseriesContainerId,
      containerName: ref.referencedContainerName,
      interval: `${toShortDateTimeString(parseDateFromNanos(ref.start))} - ${toShortDateTimeString(parseDateFromNanos(ref.end))}`,
    };
  }
  if (instanceOfFileReference(ref))
    return {
      containerId: ref.fileContainerId,
      containerName: ref.referencedContainerName,
      fileCount: ref.fileOids.length,
    };
  return {
    containerId: ref.structuredDataContainerId,
    containerName: ref.referencedContainerName,
  };
};

const tableItems: Array<TableElement> = props.dataReferences.map(ref => ({
  type: mapRefType(ref),
  name: ref.name,
  meta: {
    id: ref.id,
    ...mapContainerMetaData(ref),
  },
  created: { createdAt: ref.createdAt, createdBy: ref.createdBy },
}));

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
    sort: (a: TableElement["meta"], b: TableElement["meta"]) => a.id - b.id,
  },
  {
    title: "Created",
    value: "created",
    sort: (a: TableElement["created"], b: TableElement["created"]) =>
      a.createdAt.valueOf() - b.createdAt.valueOf(),
  },
];

const page = ref<number>(1);
const itemsPerPage = 10;

const pageCount = Math.ceil(tableItems.length / itemsPerPage);
</script>

<template>
  <CommonDataTable
    v-model:page="page"
    :headers="headers"
    :items="tableItems"
    :items-per-page="itemsPerPage"
  >
    <template #[`item.meta`]="{ value }: { value: TableElement['meta'] }">
      <DataObjectDataMetaCell
        :id="value.id"
        :container-id="value.containerId"
        :container-name="value.containerName"
        :file-count="value.fileCount"
        :interval="value.interval"
      />
    </template>
    <template #[`item.created`]="{ value }: { value: TableElement['created'] }">
      <DataObjectDataCreatedCell
        :created-at="value.createdAt"
        :created-by="value.createdBy"
      />
    </template>
    <template #bottom>
      <v-divider :thickness="8" />
      <v-pagination v-model="page" :length="pageCount" />
    </template>
  </CommonDataTable>
</template>
