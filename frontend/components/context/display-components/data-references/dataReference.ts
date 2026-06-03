import type {
  FileReference,
  StructuredDataReference,
  TimeseriesReference,
} from "@dlr-shepard/backend-client";

export type DataReferenceWithoutContainerName =
  | TimeseriesReference
  | FileReference
  | StructuredDataReference;

export type DataReference = DataReferenceWithoutContainerName &
  ReferencedContainerMeta;

export type ReferencedContainerMeta =
  | {
      referencedContainerAvailability: "available";
      referencedContainerName: string;
      /** UUID v7 appId of the referenced container. Present when the container
       *  was created after L2a (non-null appId). Used by the version-history
       *  panel on the FileReference detail page (UI7). */
      referencedContainerAppId?: string;
    }
  | {
      referencedContainerAvailability: "deleted" | "forbidden" | "error";
      referencedContainerName?: undefined;
      referencedContainerAppId?: undefined;
    };
