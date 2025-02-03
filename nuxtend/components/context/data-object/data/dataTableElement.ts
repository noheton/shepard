export type DataTableElement = {
  type: "TimeSeries" | "Structured Data" | "File";
  name: string;
  meta: {
    id: number;
    containerId: number;
    containerName: string;
    interval?: string;
    fileCount?: number;
  };
  created: {
    createdBy: string;
    createdAt: Date;
  };
};
