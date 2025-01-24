import { ContainerType } from "@dlr-shepard/backend-client";

export enum ContainerTypeName {
  FILE = "File",
  TIMESERIES = "Timeseries",
  STRUCTUREDDATA = "Structured data",
}

export const { ["Basic"]: _, ...ContainerFilterTypes } = ContainerType;
export type ContainerFilterType =
  (typeof ContainerFilterTypes)[keyof typeof ContainerFilterTypes];
