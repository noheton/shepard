import type { ContainerType } from "@dlr-shepard/backend-client";

export const containerTypeUrlPathSegmentMappings: {
  [containerType in ContainerType]: string;
} = {
  FILE: "files/",
  STRUCTUREDDATA: "structureddata/",
  TIMESERIES: "timeseries/",
  SPATIALDATA: "spatialdata/",
  BASIC: "basic/",
};
