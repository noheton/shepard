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
    }
  | {
      referencedContainerAvailability: "deleted" | "forbidden" | "error";
      referencedContainerName?: undefined;
    };
