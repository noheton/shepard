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

export function buildContainerPathByAppId(
  containerType: ContainerType,
  containerAppId: string,
) {
  switch (containerType) {
    case "FILE": {
      return fileContainerPath + containerAppId;
    }
    case "SPATIALDATA": {
      return spatialdataContainerPath + containerAppId;
    }
    case "STRUCTUREDDATA": {
      return structureddataContainerPath + containerAppId;
    }
    case "TIMESERIES": {
      return timeseriesContainerPath + containerAppId;
    }
    default: {
      throw new Error("Unknown container type: " + containerType);
    }
  }
}
