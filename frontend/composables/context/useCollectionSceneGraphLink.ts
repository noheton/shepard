/**
 * COLL-SCENE-1-UI — typed wrapper around
 * `/v2/collections/{appId}/scene-graph` (GET/PUT/DELETE).
 *
 * Mirrors the shape of `useSceneGraph.ts` — raw fetch (the v2 surface is
 * not in the generated `@dlr-shepard/backend-client` yet), v2 base URL
 * derived from runtime config, bearer-token auth, and an inline error
 * shape readable by the parent component for empty-state rendering.
 *
 * Status code semantics (per `CollectionSceneGraphRest` Javadoc):
 *  - 200 → linked, returns `CollectionSceneGraphLinkIO`.
 *  - 404 → either no scene linked OR Collection not found. The parent
 *          page already knows whether the Collection exists, so a 404
 *          here is treated as "no scene linked → render link button".
 *  - 401 → re-auth needed.
 *  - 403 → caller cannot read this Collection.
 */

export interface CollectionSceneGraphLinkIO {
  sceneGraphAppId: string;
  name?: string | null;
  description?: string | null;
  rootFrameAppId?: string | null;
  sourceFileAppId?: string | null;
  frameCount?: number | null;
  jointCount?: number | null;
}

export interface CollectionSceneGraphLinkError {
  status: number;
  message: string;
  detail: string;
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

/** Resolve the linked scene for a Collection. Returns `null` on 404 / error. */
export async function fetchCollectionSceneGraphLink(
  collectionAppId: string,
  accessToken: string,
): Promise<CollectionSceneGraphLinkIO | null> {
  if (!collectionAppId || !accessToken) return null;
  const url = `${v2BaseUrl()}/v2/collections/${encodeURIComponent(collectionAppId)}/scene-graph`;
  const resp = await fetch(url, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
      Accept: "application/json",
    },
  });
  if (!resp.ok) return null;
  return (await resp.json()) as CollectionSceneGraphLinkIO;
}

/** Link / replace the Collection's hero scene. Returns the new link on 200, null on error. */
export async function linkCollectionSceneGraph(
  collectionAppId: string,
  sceneGraphAppId: string,
  accessToken: string,
): Promise<CollectionSceneGraphLinkIO | null> {
  if (!collectionAppId || !sceneGraphAppId || !accessToken) return null;
  const url = `${v2BaseUrl()}/v2/collections/${encodeURIComponent(collectionAppId)}/scene-graph`;
  const resp = await fetch(url, {
    method: "PUT",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      Accept: "application/json",
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ sceneGraphAppId }),
  });
  if (!resp.ok) return null;
  return (await resp.json()) as CollectionSceneGraphLinkIO;
}

/** Unlink the Collection's hero scene. Returns `true` on 204. */
export async function unlinkCollectionSceneGraph(
  collectionAppId: string,
  accessToken: string,
): Promise<boolean> {
  if (!collectionAppId || !accessToken) return false;
  const url = `${v2BaseUrl()}/v2/collections/${encodeURIComponent(collectionAppId)}/scene-graph`;
  const resp = await fetch(url, {
    method: "DELETE",
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  return resp.status === 204 || resp.ok;
}

/**
 * Fetch the URDF XML for a scene via `GET /v2/scene-graphs/{appId}/export.urdf`
 * and return a blob:URL that `UrdfCanvas` can consume. The caller is
 * responsible for revoking the URL when the component unmounts (parent
 * uses `URL.revokeObjectURL`).
 */
export async function fetchSceneUrdfBlobUrl(
  sceneAppId: string,
  accessToken: string,
): Promise<string | null> {
  if (!sceneAppId || !accessToken) return null;
  const url = `${v2BaseUrl()}/v2/scene-graphs/${encodeURIComponent(sceneAppId)}/export.urdf`;
  const resp = await fetch(url, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
      Accept: "application/xml",
    },
  });
  if (!resp.ok) return null;
  const xml = await resp.text();
  const blob = new Blob([xml], { type: "application/xml" });
  return URL.createObjectURL(blob);
}
