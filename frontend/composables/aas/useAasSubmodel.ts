/**
 * MISSING-aas-ui Slice 10/13 — composable fetching a DataObject in AAS Submodel shape,
 * plus its semantic annotations as AAS SubmodelElement Properties.
 *
 * DataObject detail: GET /v2/collections/{collectionAppId}/data-objects/{dataObjectAppId}
 * Annotations:       GET /v2/annotations?subjectAppId={doAppId}&subjectKind=DataObject&pageSize=200
 *
 * No new backend endpoints required — both are existing v2 surfaces.
 */

const DATAOBJECT_URN_PREFIX = "urn:shepard:dataobject:";

/** Compute the AAS Submodel IRI from a DataObject appId. */
export function dataObjectAppIdToSubmodelIri(appId: string): string {
  return `${DATAOBJECT_URN_PREFIX}${appId}`;
}

export interface AasSubmodelDetailIO {
  /** AAS idShort — maps to DataObject.name */
  idShort: string;
  /** AAS Submodel IRI — urn:shepard:dataobject:{appId} */
  id: string;
  /** DataObject description (may be empty) */
  description: string;
  /** Raw DataObject appId for constructing links */
  appId: string;
  /** Parent Collection appId (= shellId) */
  collectionAppId: string;
}

/** One AAS Property element derived from a semantic annotation. */
export interface AasPropertyElementIO {
  /** AAS idShort: last path segment of predicateIri, or predicateName, or raw IRI */
  idShort: string;
  /** Full predicate IRI */
  predicateIri: string;
  /** Human-readable predicate name when available */
  predicateName: string | null;
  /** Literal value (for data-valued annotations) */
  objectLiteral: string | null;
  /** IRI value (for object-valued annotations) */
  objectIri: string | null;
  /** Display value — objectLiteral preferred, objectIri as fallback */
  displayValue: string;
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

/** Derive a short AAS-style idShort from a full predicate IRI. */
export function predicateIriToIdShort(iri: string): string {
  const lastHash = iri.lastIndexOf("#");
  const lastSlash = iri.lastIndexOf("/");
  const lastColon = iri.lastIndexOf(":");
  const pos = Math.max(lastHash, lastSlash, lastColon);
  const segment = pos >= 0 ? iri.slice(pos + 1) : iri;
  // Replace characters invalid for AAS idShort
  const sanitised = segment.replace(/[^a-zA-Z0-9_]/g, "_");
  return sanitised || "property";
}

/**
 * Fetch a single AAS Submodel by Collection + DataObject appId.
 * Also fetches its semantic annotations as AAS Property elements.
 */
export function useAasSubmodel(collectionAppId: string, dataObjectAppId: string) {
  const submodel = ref<AasSubmodelDetailIO | null>(null);
  const properties = ref<AasPropertyElementIO[]>([]);
  const isLoading = ref(false);
  const isPropertiesLoading = ref(false);
  const isNotFound = ref(false);
  const error = ref<string | null>(null);

  async function refresh() {
    isLoading.value = true;
    error.value = null;
    isNotFound.value = false;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      const url =
        `${v2BaseUrl()}/v2/collections/` +
        `${encodeURIComponent(collectionAppId)}/data-objects/` +
        `${encodeURIComponent(dataObjectAppId)}`;
      const response = await fetch(url, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: "application/json",
        },
      });
      if (response.status === 404) {
        isNotFound.value = true;
        return;
      }
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const data = await response.json();
      submodel.value = {
        idShort: data.name ?? dataObjectAppId,
        id: dataObjectAppIdToSubmodelIri(dataObjectAppId),
        description: data.description ?? "",
        appId: dataObjectAppId,
        collectionAppId,
      };
    } catch (e) {
      error.value = "Failed to load Submodel";
      handleError(e, "fetching AAS Submodel");
    } finally {
      isLoading.value = false;
    }

    // Fetch annotations in parallel (non-blocking)
    fetchProperties();
  }

  async function fetchProperties() {
    isPropertiesLoading.value = true;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      const params = new URLSearchParams({
        subjectAppId: dataObjectAppId,
        subjectKind: "DataObject",
        pageSize: "200",
      });
      const url = `${v2BaseUrl()}/v2/annotations?${params}`;
      const response = await fetch(url, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: "application/json",
        },
      });
      if (!response.ok) {
        // Non-fatal: property listing is secondary to submodel identity
        return;
      }
      const data = await response.json();
      const items: unknown[] = Array.isArray(data.items) ? data.items : [];
      properties.value = items.map((item: unknown) => {
        const a = item as Record<string, unknown>;
        const iri = String(a.predicateIri ?? "");
        const name = a.predicateName ? String(a.predicateName) : null;
        const lit = a.objectLiteral ? String(a.objectLiteral) : null;
        const obj = a.objectIri ? String(a.objectIri) : null;
        return {
          idShort: name ? name.replace(/[^a-zA-Z0-9_]/g, "_") : predicateIriToIdShort(iri),
          predicateIri: iri,
          predicateName: name,
          objectLiteral: lit,
          objectIri: obj,
          displayValue: lit ?? obj ?? "",
        } as AasPropertyElementIO;
      });
    } catch {
      // Silently fail — property listing is enhancement, not core
    } finally {
      isPropertiesLoading.value = false;
    }
  }

  refresh();

  return {
    submodel,
    properties,
    isLoading,
    isPropertiesLoading,
    isNotFound,
    error,
    refresh,
  };
}
