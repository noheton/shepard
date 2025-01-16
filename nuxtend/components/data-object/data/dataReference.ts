import type {
  FileReference,
  StructuredDataReference,
  TimeseriesReference,
} from "@dlr-shepard/backend-client";

export type DataReferenceWithoutContainerName =
  | TimeseriesReference
  | FileReference
  | StructuredDataReference;

export type DataReference = DataReferenceWithoutContainerName & {
  referencedContainerName: string;
};
