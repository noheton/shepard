import type { ReferencedContainerMeta } from "./dataReference";

export type DataTableElement = {
  type: "TimeSeries" | "Structured Data" | "File Bundle" | "Git" | "Video";
  name: string;
  meta: {
    /** Numeric id for legacy-v1 annotation path; undefined for appId-only kinds (Git/Video). */
    id?: number;
    /** appId string for SEMA-V6 annotation path; set for new kinds (Git/Video). */
    appId?: string;
    containerId?: number;
    interval?: string;
    fileCount?: number;
    payloadCount?: number;
    /** AI1c — quality score in [0.0, 1.0]; null = not yet scored; only set for TimeSeries rows. */
    qualityScore?: number | null;
    /** Git: repository URL */
    repoUrl?: string;
    /** Git: branch/tag/commit ref */
    gitRef?: string;
    /** Git: subdirectory path within repo */
    gitPath?: string;
    /** Video: duration in seconds */
    durationSeconds?: number | null;
    /** Video: WxH resolution string e.g. "1920×1080" */
    resolution?: string | null;
  } & Partial<ReferencedContainerMeta>;
  created: {
    createdBy: string;
    createdAt: Date;
  };
  actions: {
    /** Numeric id for legacy action routing; undefined for new kinds. */
    elementId?: number;
    /** appId string for new kinds. */
    elementAppId?: string;
    showDetails: {
      enabled: boolean;
      pathFragment: string;
    };
  };
};
