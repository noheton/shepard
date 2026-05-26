import type { ReferencedContainerMeta } from "./dataReference";

export type DataTableElement = {
  type: "TimeSeries" | "Structured Data" | "File Bundle";
  name: string;
  meta: {
    id: number;
    containerId: number;
    interval?: string;
    fileCount?: number;
    payloadCount?: number;
    /** AI1c — quality score in [0.0, 1.0]; null = not yet scored; only set for TimeSeries rows. */
    qualityScore?: number | null;
  } & ReferencedContainerMeta;
  created: {
    createdBy: string;
    createdAt: Date;
  };
  actions: {
    elementId: number;
    showDetails: {
      enabled: boolean;
      pathFragment: string;
    };
  };
};
