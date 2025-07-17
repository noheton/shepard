import type { ContainerType } from "@dlr-shepard/backend-client";

export function buildContainerPath(
  containerType: ContainerType,
  containerId: number,
) {
  switch (containerType) {
    case "FILE": {
      return fileContainerPath + containerId;
    }
    case "SPATIALDATA": {
      return spatialdataContainerPath + containerId;
    }
    case "STRUCTUREDDATA": {
      return structureddataContainerPath + containerId;
    }
    case "TIMESERIES": {
      return timeseriesContainerPath + containerId;
    }
    default: {
      throw new Error("Unknown container type: " + containerType);
    }
  }
}
