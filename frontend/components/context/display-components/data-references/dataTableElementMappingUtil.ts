import {
  instanceOfFileReference,
  instanceOfStructuredDataReference,
  instanceOfTimeseriesReference,
} from "@dlr-shepard/backend-client";
import type { DataReference, ReferencedContainerMeta } from "./dataReference";
import type { DataTableElement } from "./dataTableElement";
import type { GitReferenceIO } from "~/composables/context/gitReferenceTypes";
import type { VideoStreamReferenceIO } from "~/composables/context/useFetchVideoStreamReferences";
import type { SingletonFileReferenceIO } from "~/composables/context/useFetchSingletonFileReferences";
import type { SpatialReferenceV2IO } from "~/composables/context/useFetchSpatialReferencesV2";

export const mapDataReferenceToDataTableElement = (
  ref: DataReference,
): DataTableElement => ({
  type: mapRefType(ref),
  name: ref.name,
  meta: mapContainerMetaData(ref),
  created: { createdAt: ref.createdAt, createdBy: ref.createdBy },
  actions: { elementId: ref.id, showDetails: buildShowDetailsArgs(ref) },
});

// ── New-kind mappers (Git / Video) ───────────────────────────────────────────
// These reference kinds use appId (string) rather than numeric id.
// The "Created" column shows "—" because these kinds don't carry createdAt/createdBy
// in the current API shape.

const FALLBACK_DATE = new Date(0);

export const mapGitReferenceToDataTableElement = (
  ref: GitReferenceIO,
): DataTableElement => ({
  type: "Git",
  name: ref.repoUrl,
  meta: {
    appId: ref.appId,
    repoUrl: ref.repoUrl,
    gitRef: ref.ref ?? undefined,
    gitPath: ref.path ?? undefined,
  },
  created: {
    createdAt: FALLBACK_DATE,
    createdBy: "—",
  },
  actions: {
    elementAppId: ref.appId,
    showDetails: { enabled: false, pathFragment: "" },
  },
});

/**
 * REF-UNIFIED-TABLE-FR1B — map a FR1b singleton FileReference into a
 * unified-table row. The row is classified as "Notebook" when the
 * embedded filename ends in `.ipynb` (case-insensitive); otherwise
 * "File". The "Notebook" type drives the icon + the conditional
 * "Open in JupyterHub" action; both share the same download +
 * delete affordances.
 *
 * J1c retirement: the dedicated DataObjectNotebooksPane was removed
 * in this PR — notebooks now live as regular rows here.
 */
export const isIpynbFilename = (filename: string | undefined | null): boolean =>
  !!filename && filename.toLowerCase().endsWith(".ipynb");

export const mapSingletonFileReferenceToDataTableElement = (
  ref: SingletonFileReferenceIO,
): DataTableElement => {
  const filename = ref.file?.filename;
  const isNotebook = isIpynbFilename(filename);
  return {
    type: isNotebook ? "Notebook" : "File",
    name: ref.name,
    meta: {
      appId: ref.appId,
      filename: filename ?? undefined,
      fileSize: ref.file?.fileSize ?? null,
    },
    created: {
      createdAt: ref.createdAt ? new Date(ref.createdAt) : FALLBACK_DATE,
      createdBy: ref.createdBy ?? "—",
    },
    actions: {
      elementAppId: ref.appId,
      showDetails: { enabled: false, pathFragment: "" },
    },
  };
};

export const mapVideoReferenceToDataTableElement = (
  ref: VideoStreamReferenceIO,
): DataTableElement => {
  const resolution =
    ref.width != null && ref.height != null
      ? `${ref.width}×${ref.height}`
      : null;
  return {
    type: "Video",
    name: ref.name ?? ref.appId,
    meta: {
      appId: ref.appId,
      durationSeconds: ref.durationSeconds,
      resolution,
    },
    created: {
      createdAt: FALLBACK_DATE,
      createdBy: "—",
    },
    actions: {
      elementAppId: ref.appId,
      showDetails: { enabled: false, pathFragment: "" },
    },
  };
};


/**
 * SPATIAL-UNIFY-003 — map a unified spatial reference (kind=spatial) into a
 * table row. Addressed by appId; the SpatialDataContainer behind it is carried
 * as `spatialContainerAppId` (the viewer target), never surfaced as a picker.
 */
export const mapSpatialReferenceToDataTableElement = (
  ref: SpatialReferenceV2IO,
): DataTableElement => ({
  type: "Spatial",
  name: ref.name ?? ref.appId,
  meta: {
    appId: ref.appId,
    spatialContainerAppId: ref.spatialDataContainerAppId ?? null,
    promotionState: ref.promotionState ?? null,
  },
  created: {
    createdAt: ref.createdAt ? new Date(ref.createdAt) : FALLBACK_DATE,
    createdBy: ref.createdBy ?? "—",
  },
  actions: {
    elementAppId: ref.appId,
    showDetails: { enabled: false, pathFragment: "" },
  },
});

const mapRefType = (ref: DataReference): DataTableElement["type"] => {
  if (instanceOfTimeseriesReference(ref)) return "TimeSeries";
  if (instanceOfFileReference(ref)) return "File Bundle";
  if (instanceOfStructuredDataReference(ref)) return "Structured Data";

  throw Error("Cannot map ref type: Unknown reference type.");
};

const mapContainerMetaData = (ref: DataReference): DataTableElement["meta"] => {
  if (instanceOfTimeseriesReference(ref)) {
    return {
      id: ref.id,
      containerId: ref.timeseriesContainerId,
      ...mapNameAndAvailability(ref),
      interval: `${toShortDateTimeString(parseDateFromNanos(ref.start))} - ${toShortDateTimeString(parseDateFromNanos(ref.end))}`,
      // AI1c — carry the quality score so the table can show a chip
      qualityScore: ref.qualityScore ?? null,
    };
  }
  if (instanceOfFileReference(ref))
    return {
      id: ref.id,
      containerId: ref.fileContainerId,
      ...mapNameAndAvailability(ref),
      fileCount: ref.fileOids.length,
    };
  if (instanceOfStructuredDataReference(ref))
    return {
      id: ref.id,
      containerId: ref.structuredDataContainerId,
      ...mapNameAndAvailability(ref),
      payloadCount: ref.structuredDataOids.length,
    };

  throw Error("Cannot map container meta data: Unknown reference type.");
};

const mapNameAndAvailability = (
  meta: ReferencedContainerMeta,
): ReferencedContainerMeta => {
  if (meta.referencedContainerAvailability !== "available")
    return {
      referencedContainerAvailability: meta.referencedContainerAvailability,
    };

  return {
    referencedContainerAvailability: meta.referencedContainerAvailability,
    referencedContainerName: meta.referencedContainerName,
  };
};

function buildShowDetailsArgs(ref: DataReference): {
  enabled: boolean;
  pathFragment: string;
} {
  const refType = mapRefType(ref);
  if (refType === "TimeSeries") {
    return { enabled: true, pathFragment: timeseriesReferencePathFragment };
  }
  if (refType === "File Bundle") {
    return { enabled: true, pathFragment: fileReferencesPathFragment };
  }
  if (refType === "Structured Data") {
    return {
      enabled: true,
      pathFragment: structuredDataReferencesPathFragment,
    };
  }
  return {
    enabled: false,
    pathFragment: "",
  };
}
