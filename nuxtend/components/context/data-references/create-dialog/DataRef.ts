import type {
  FileReference,
  SpatialDataReference,
  StructuredDataReference,
  TimeseriesReference,
} from "@dlr-shepard/backend-client";

export type DataRef =
  | FileRef
  | TimeseriesRef
  | SpatialDataRef
  | StructuredDataRef;

export type FileRef = Omit<
  FileReference,
  | "id"
  | "name"
  | "fileContainerId"
  | "createdAt"
  | "createdBy"
  | "updatedAt"
  | "updatedBy"
  | "dataObjectId"
  | "type"
>;
export type TimeseriesRef = Omit<
  TimeseriesReference,
  | "id"
  | "name"
  | "timeseriesContainerId"
  | "createdAt"
  | "createdBy"
  | "updatedAt"
  | "updatedBy"
  | "dataObjectId"
  | "type"
>;
export type StructuredDataRef = Omit<
  StructuredDataReference,
  | "id"
  | "name"
  | "structuredDataContainerId"
  | "createdAt"
  | "createdBy"
  | "updatedAt"
  | "updatedBy"
  | "dataObjectId"
  | "type"
>;
export type SpatialDataRef = Omit<
  SpatialDataReference,
  | "id"
  | "name"
  | "spatialDataContainerId"
  | "createdAt"
  | "createdBy"
  | "updatedAt"
  | "updatedBy"
  | "dataObjectId"
  | "type"
>;
