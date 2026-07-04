/**
 * APISIMP-REF-CREATE-NUMERIC-IDS / APISIMP-CREATE-REFS-V2 — create collection,
 * dataobject, and uri references via the unified POST /v2/references?kind=...
 * surface, using appId strings throughout. Supersedes the v1
 * CollectionReferenceApi / DataObjectReferenceApi / UriReferenceApi calls in
 * useCreateReferences.ts for callers that have a dataObjectAppId.
 *
 * Pattern mirrors useManageGitReferences.ts (raw fetch against v2BaseUrl).
 */

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

function authHeaders(): Record<string, string> {
  const { data: session } = useAuth();
  const accessToken = session.value?.accessToken;
  return accessToken ? { Authorization: `Bearer ${accessToken}` } : {};
}

export function useCreateReferencesV2(
  dataObjectAppId: string,
  onSuccess: () => void,
  isLoading?: Ref<boolean>,
) {
  const loading = isLoading ?? ref<boolean>(false);

  async function addCollectionReference(
    referencedCollectionAppId: string,
    name: string,
    relationship?: string,
  ) {
    loading.value = true;
    try {
      const url =
        `${v2BaseUrl()}/v2/references` +
        `?kind=collection&dataObjectAppId=${encodeURIComponent(dataObjectAppId)}`;
      const res = await fetch(url, {
        method: "POST",
        headers: { ...authHeaders(), "Content-Type": "application/json" },
        body: JSON.stringify({
          name,
          referencedCollectionAppId,
          relationship: relationship ?? "Collection",
        }),
      });
      if (!res.ok) throw new Error(`createCollectionReference failed: ${res.status}`);
      emitSuccess("Successfully created Collection reference");
      handleDataObjectUpdate();
      onSuccess();
    } catch (error) {
      handleError(error, "createCollectionReference");
    } finally {
      loading.value = false;
    }
  }

  async function addDataObjectReference(
    referencedDataObjectAppId: string,
    name: string,
    relationship?: string,
  ) {
    loading.value = true;
    try {
      const url =
        `${v2BaseUrl()}/v2/references` +
        `?kind=dataobject&dataObjectAppId=${encodeURIComponent(dataObjectAppId)}`;
      const res = await fetch(url, {
        method: "POST",
        headers: { ...authHeaders(), "Content-Type": "application/json" },
        body: JSON.stringify({
          name,
          referencedDataObjectAppId,
          relationship: relationship ?? "Data Object",
        }),
      });
      if (!res.ok) throw new Error(`createDataObjectReference failed: ${res.status}`);
      emitSuccess("Successfully created Data Object reference");
      handleDataObjectUpdate();
      onSuccess();
    } catch (error) {
      handleError(error, "createDataObjectReference");
    } finally {
      loading.value = false;
    }
  }

  async function addUriReferenceV2(
    uri: string,
    name: string,
    relationship?: string,
  ) {
    loading.value = true;
    try {
      const url =
        `${v2BaseUrl()}/v2/references` +
        `?kind=uri&dataObjectAppId=${encodeURIComponent(dataObjectAppId)}`;
      const res = await fetch(url, {
        method: "POST",
        headers: { ...authHeaders(), "Content-Type": "application/json" },
        body: JSON.stringify({
          name,
          uri,
          relationship: relationship ?? "URI",
        }),
      });
      if (!res.ok) throw new Error(`createUriReference failed: ${res.status}`);
      emitSuccess("Successfully created URI reference");
      handleDataObjectUpdate();
      onSuccess();
    } catch (error) {
      handleError(error, "createUriReference");
    } finally {
      loading.value = false;
    }
  }

  return { addCollectionReference, addDataObjectReference, addUriReferenceV2, loading };
}
