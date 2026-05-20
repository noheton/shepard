/**
 * Lightweight container summary returned by
 * GET /v2/collections/{appId}/referenced-containers (CC2).
 */
export interface ContainerSummary {
  /** Neo4j OGM id — use for legacy navigation routes. */
  id: number;
  /** Application-level UUID-v7 identifier. */
  appId: string | null;
  /** Display name. */
  name: string | null;
  /** Container kind. */
  containerType: "TIMESERIES" | "FILE" | "STRUCTUREDDATA" | "BASIC";
}
