import type {
  StructuredData,
  StructuredDataReference,
} from "@dlr-shepard/backend-client";
import type { ReferencedContainerMeta } from "../data-references/dataReference";

export type StructuredDataMeta = StructuredData & {
  availability: "available" | "deleted" | "error";
  payload: string;
};

export type StructuredDataReferenceWithContainerMeta = StructuredDataReference &
  ReferencedContainerMeta;
