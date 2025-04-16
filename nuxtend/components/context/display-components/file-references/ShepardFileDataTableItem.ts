import type { FileMeta } from "./fileReferenceTypes";

export type FileType = "image" | "text" | "unknown";

export type ShepardFileDataTableItem = {
  name: { filename: string; availability: FileMeta["availability"] };
  oid: string;
  createdAt: Date;
  actions: {
    showDetails: {
      enabled: boolean;
      oid: string;
      fileType: FileType;
    };
    download: {
      enabled: boolean;
      filename: string;
      oid: string;
    };
  };
};
