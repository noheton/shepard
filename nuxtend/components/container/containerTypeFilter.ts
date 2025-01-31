import { ContainerType } from "@dlr-shepard/backend-client";

export enum ContainerTypeName {
  FILE = "File",
  TIMESERIES = "Timeseries",
  STRUCTUREDDATA = "Structured data",
}

export const { ["Basic"]: _, ...ContainerFilterTypes } = ContainerType;
export type ContainerFilterType =
  (typeof ContainerFilterTypes)[keyof typeof ContainerFilterTypes];

export function instanceOfContainerFilterType(
  value: string,
): value is ContainerFilterType {
  return Object.values(ContainerFilterTypes).includes(
    value as ContainerFilterType,
  );
}
