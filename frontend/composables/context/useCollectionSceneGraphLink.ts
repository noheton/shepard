/**
 * V2CONV-B4-FE — typed wrapper around `/v2/collections/{appId}/scene-graph`
 * (GET/PUT/DELETE).
 *
 * The bespoke scene-graph subsystem dissolved into the generic MAPPING_RECIPE
 * mechanism (aidocs/platform/191 decision #2). The Collection's hero link now
 * points at a MAPPING_RECIPE `ShepardTemplate` appId (a "hero view") rather than
 * a `:DigitalTwinScene`. The endpoint path + the JSON field `sceneGraphAppId`
 * are kept unchanged so existing callers don't break — only the referent
 * changed.
 *
 * Status code semantics (per `CollectionSceneGraphRest` Javadoc):
 *  - 200 → linked, returns `CollectionHeroViewLinkIO`.
 *  - 404 → no hero view linked OR Collection not found → render link button.
 *  - 401 → re-auth needed; 403 → caller cannot read this Collection;
 *  - 422 → target is not a MAPPING_RECIPE template (PUT only).
 */

export interface CollectionHeroViewLinkIO {
  /** appId of the linked MAPPING_RECIPE template (the hero view). */
  sceneGraphAppId: string;
  templateName?: string | null;
  templateDescription?: string | null;
  templateKind?: string | null;
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

/** Resolve the linked hero-view template for a Collection. Returns `null` on 404 / error. */
export async function fetchCollectionSceneGraphLink(
  collectionAppId: string,
  accessToken: string,
): Promise<CollectionHeroViewLinkIO | null> {
  if (!collectionAppId || !accessToken) return null;
  const url = `${v2BaseUrl()}/v2/collections/${encodeURIComponent(collectionAppId)}/scene-graph`;
  const resp = await fetch(url, {
    headers: { Authorization: `Bearer ${accessToken}`, Accept: "application/json" },
  });
  if (!resp.ok) return null;
  return (await resp.json()) as CollectionHeroViewLinkIO;
}

/** Link / replace the Collection's hero view. Returns the new link on 200, null on error. */
export async function linkCollectionSceneGraph(
  collectionAppId: string,
  templateAppId: string,
  accessToken: string,
): Promise<CollectionHeroViewLinkIO | null> {
  if (!collectionAppId || !templateAppId || !accessToken) return null;
  const url = `${v2BaseUrl()}/v2/collections/${encodeURIComponent(collectionAppId)}/scene-graph`;
  const resp = await fetch(url, {
    method: "PUT",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      Accept: "application/json",
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ sceneGraphAppId: templateAppId }),
  });
  if (!resp.ok) return null;
  return (await resp.json()) as CollectionHeroViewLinkIO;
}

/** Unlink the Collection's hero view. Returns `true` on 204. */
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

export interface HeroViewTemplateOption {
  appId: string;
  name?: string | null;
}

/**
 * List MAPPING_RECIPE templates the caller can link as a hero view. Pulls from
 * `GET /v2/templates?kind=MAPPING_RECIPE`. Returns [] on error.
 */
export async function listHeroViewTemplates(
  accessToken: string,
): Promise<HeroViewTemplateOption[]> {
  if (!accessToken) return [];
  const url = `${v2BaseUrl()}/v2/templates?kind=MAPPING_RECIPE`;
  const resp = await fetch(url, {
    headers: { Authorization: `Bearer ${accessToken}`, Accept: "application/json" },
  });
  if (!resp.ok) return [];
  const body = (await resp.json()) as
    | { items?: HeroViewTemplateOption[] }
    | HeroViewTemplateOption[];
  const items = Array.isArray(body) ? body : (body.items ?? []);
  return items.map((t) => ({ appId: t.appId, name: t.name }));
}
