import {
  instanceOfFileReference,
  instanceOfStructuredDataReference,
  instanceOfTimeseriesReference,
} from "@dlr-shepard/backend-client";
import type { DataReference, ReferencedContainerMeta } from "./dataReference";
import type { DataTableElement } from "./dataTableElement";

export const mapDataReferenceToDataTableElement = (
  ref: DataReference,
): DataTableElement => ({
  type: mapRefType(ref),
  name: ref.name,
  meta: mapContainerMetaData(ref),
  created: { createdAt: ref.createdAt, createdBy: ref.createdBy },
  actions: { elementId: ref.id, showDetails: buildShowDetailsArgs(ref) },
});

const mapRefType = (ref: DataReference): DataTableElement["type"] => {
  if (instanceOfTimeseriesReference(ref)) return "TimeSeries";
  if (instanceOfFileReference(ref)) return "File Bundle";
  if (instanceOfStructuredDataReference(ref)) return "Structured Data";

  throw Error("Cannot map ref type: Unknown reference type.");
};

const mapContainerMetaData = (ref: DataReference): DataTableElement["meta"] => {
  if (instanceOfTimeseriesReference(ref)) {
    return {
      id: ref.id,
      containerId: ref.timeseriesContainerId,
      ...mapNameAndAvailability(ref),
      interval: `${toShortDateTimeString(parseDateFromNanos(ref.start))} - ${toShortDateTimeString(parseDateFromNanos(ref.end))}`,
    };
  }
  if (instanceOfFileReference(ref))
    return {
      id: ref.id,
      containerId: ref.fileContainerId,
      ...mapNameAndAvailability(ref),
      fileCount: ref.fileOids.length,
    };
  if (instanceOfStructuredDataReference(ref))
    return {
      id: ref.id,
      containerId: ref.structuredDataContainerId,
      ...mapNameAndAvailability(ref),
      payloadCount: ref.structuredDataOids.length,
    };

  throw Error("Cannot map container meta data: Unknown reference type.");
};

const mapNameAndAvailability = (
  meta: ReferencedContainerMeta,
): ReferencedContainerMeta => {
  if (meta.referencedContainerAvailability !== "available")
    return {
      referencedContainerAvailability: meta.referencedContainerAvailability,
    };

  return {
    referencedContainerAvailability: meta.referencedContainerAvailability,
    referencedContainerName: meta.referencedContainerName,
  };
};

function buildShowDetailsArgs(ref: DataReference): {
  enabled: boolean;
  pathFragment: string;
} {
  const refType = mapRefType(ref);
  if (refType === "TimeSeries") {
    return { enabled: true, pathFragment: timeseriesReferencePathFragment };
  }
  if (refType === "File Bundle") {
    return { enabled: true, pathFragment: fileReferencesPathFragment };
  }
  if (refType === "Structured Data") {
    return {
      enabled: true,
      pathFragment: structuredDataReferencesPathFragment,
    };
  }
  return {
    enabled: false,
    pathFragment: "",
  };
}
