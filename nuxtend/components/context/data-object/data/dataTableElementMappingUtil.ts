import {
  instanceOfFileReference,
  instanceOfTimeseriesReference,
} from "@dlr-shepard/backend-client";
import type { DataReference } from "./dataReference";
import type { DataTableElement } from "./dataTableElement";

export const mapDataReferenceToDataTableElement = (ref: DataReference) => ({
  type: mapRefType(ref),
  name: ref.name,
  meta: {
    id: ref.id,
    ...mapContainerMetaData(ref),
  },
  created: { createdAt: ref.createdAt, createdBy: ref.createdBy },
});

const mapRefType = (ref: DataReference): DataTableElement["type"] => {
  if (instanceOfTimeseriesReference(ref)) return "TimeSeries";
  if (instanceOfFileReference(ref)) return "File";
  return "Structured Data";
};

const mapContainerMetaData = (
  ref: DataReference,
): Omit<DataTableElement["meta"], "id"> => {
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
