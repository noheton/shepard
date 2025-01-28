import type { CreateCollectionRequest } from "@dlr-shepard/backend-client";

export type CollectionToCreate = CreateCollectionRequest["collection"] & {
  description: string;
  attributes: { [key: string]: string };
};
