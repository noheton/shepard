import type { ReferencedContainerMeta } from "./dataReference";

export type DataTableElement = {
  type: "TimeSeries" | "Structured Data" | "File";
  name: string;
  meta: {
    id: number;
    containerId: number;
    interval?: string;
    fileCount?: number;
    payloadCount?: number;
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
