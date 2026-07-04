import type {
  FileReference,
  StructuredDataReference,
  TimeseriesReference,
} from "@dlr-shepard/backend-client";

export type DataReferenceWithoutContainerName =
  | TimeseriesReference
  | FileReference
  | StructuredDataReference;

/**
 * REFS-V2-PANELS — stable per-kind discriminator stamped by
 * useDataReferencesByDataObject when it flattens a v2 ReferenceV2IO into the
 * v1-shaped DataReference. The generated `instanceOf*` guards require a numeric
 * `id` that v2-sourced refs never carry, so the mapping util dispatches on this
 * field instead. Optional so any legacy v1-shaped DataReference still type-checks.
 */
export type DataReferenceKind = "timeseries" | "bundle" | "structured-data";

export type DataReference = DataReferenceWithoutContainerName &
  ReferencedContainerMeta & {
    /** REFS-V2-PANELS discriminator; present on v2-sourced refs. */
    __refKind?: DataReferenceKind;
  };

export type ReferencedContainerMeta =
  | {
      referencedContainerAvailability: "available";
      referencedContainerName: string;
    }
  | {
      referencedContainerAvailability: "deleted" | "forbidden" | "error";
      referencedContainerName?: undefined;
    };
