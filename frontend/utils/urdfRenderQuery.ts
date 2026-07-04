import type { LocationQuery } from "vue-router";

/**
 * V2-SWEEP Wave 2 — parse the URDF bootstrap query for /shapes/render.
 *
 * Canonical shape: `?renderer=urdf&urdfFileAppId=<FileReference appId>` —
 * the page resolves the bytes via `GET /v2/files/{appId}/content`
 * (useUrdfReferenceBlob). "UI never asks for paths/URLs — pulls from
 * references."
 *
 * Legacy shape (`?urdfUrl=…&packagePath=…`) is honoured for one
 * deprecation window (old bookmarks + the static /urdf-samples/ demo
 * assets); removal tracked under UI-PATHS-FROM-REFERENCES in aidocs/16.
 * When BOTH are present the appId wins — the reference is the canonical
 * addressing layer.
 */
export interface UrdfRenderBootstrap {
  /** Resolve bytes from this FileReference appId (canonical path). */
  fileReferenceAppId?: string;
  /** DEPRECATED — raw URL/path carried by a legacy bookmark. */
  legacyUrl?: string;
  /** DEPRECATED — mesh package root carried by a legacy bookmark. */
  legacyPackagePath?: string;
}

export function parseUrdfRenderQuery(q: LocationQuery): UrdfRenderBootstrap {
  const appId = typeof q.urdfFileAppId === "string" ? q.urdfFileAppId : "";
  if (appId.length > 0) return { fileReferenceAppId: appId };
  return {
    legacyUrl: q.urdfUrl
      ? decodeURIComponent(String(q.urdfUrl))
      : "/urdf-samples/two-link-arm.urdf",
    legacyPackagePath: q.packagePath
      ? decodeURIComponent(String(q.packagePath))
      : "",
  };
}

/** True when the query asks for the URDF renderer at all. */
export function isUrdfRenderQuery(q: LocationQuery): boolean {
  return (
    q.renderer === "urdf" ||
    ((q.urdfFileAppId !== undefined || q.urdfUrl !== undefined) && !q.roles)
  );
}
