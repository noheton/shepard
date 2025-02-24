interface Base {
  X: number;
  Y: number;
  Z: number;
  A: number;
  B: number;
  C: number;
  baseName: string;
}

export interface Metadata {
  track?: number;
  layer?: number;
  name?: string;
  base?: Base;
  isSuccess?: boolean;
  orientation?: number[];
  "MTLH_TCP in BASE"?: { "can be found in corresponding fsd csv-file.": boolean };
}

interface Measurements {
  value: number;
  data: number[];
}

export interface SpatialDataPoint {
  x: number;
  y: number;
  z: number;
  timestamp: number;
  metadata: Metadata;
  measurements: Measurements;
}

export interface BoundingBoxFilter {
  type: string;
  minX: number;
  minY: number;
  minZ: number;
  maxX: number;
  maxY: number;
  maxZ: number;
}

export function createBoundingBoxFilter(
  minX: number,
  minY: number,
  minZ: number,
  maxX: number,
  maxY: number,
  maxZ: number,
): BoundingBoxFilter {
  return { type: "AXIS_ALIGNED_BOUNDING_BOX", minX, minY, minZ, maxX, maxY, maxZ };
}

export interface BoundingSphere {
  type: string;
  radius: number;
  centerX: number;
  centerY: number;
  centerZ: number;
}

export function createBoundingSphereFilter(
  radius: number,
  centerX: number,
  centerY: number,
  centerZ: number,
): BoundingSphere {
  return { type: "BOUNDING_SPHERE", radius, centerX, centerY, centerZ };
}

export interface KNearestNeighbor {
  type: string;
  k: number;
  x: number;
  y: number;
  z: number;
}

export function createKNearestNeighborFilter(k: number, x: number, y: number, z: number): KNearestNeighbor {
  return { type: "K_NEAREST_NEIGHBOR", k, x, y, z };
}

export interface SpatialFilter {
  geometryFilter: BoundingBoxFilter | BoundingSphere | KNearestNeighbor;
  metadata?: Metadata;
  startTime?: number;
  endTime?: number;
  limit?: number;
  offset?: number;
  skip?: number;
}
