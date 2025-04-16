import type { FileReference, ShepardFile } from "@dlr-shepard/backend-client";
import type { ReferencedContainerMeta } from "../data-references/dataReference";

export type FileMeta = ShepardFile & {
  availability: "available" | "deleted" | "error";
};

export type FileReferenceWithContainerMeta = FileReference &
  ReferencedContainerMeta;
