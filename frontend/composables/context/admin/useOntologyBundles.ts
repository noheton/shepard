/**
 * ONT1d — composable wrapping the N1c2 ontology-bundle admin endpoints:
 *
 *   GET    /v2/admin/semantic/ontologies
 *   POST   /v2/admin/semantic/ontologies/{bundleId}/enable
 *   POST   /v2/admin/semantic/ontologies/{bundleId}/disable
 *   POST   /v2/admin/semantic/ontologies   (multipart: file + metadata JSON)
 *   DELETE /v2/admin/semantic/ontologies/{bundleId}
 *
 * Raw fetch (no generated client) — same pattern as useFetchPlugins.ts.
 */

export interface OntologyBundleIO {
  id: string;
  name?: string | null;
  source: "builtin" | "user";
  required: boolean;
  enabled: boolean;
  iriPrefix?: string | null;
  canonicalUrl?: string | null;
  license?: string | null;
  sha256?: string | null;
  byteSize: number;
}

export interface OntologyBundleListIO {
  items: OntologyBundleIO[];
}

export interface UploadOntologyMetadata {
  id: string;
  name?: string;
  iriPrefix?: string;
  canonicalUrl?: string;
  license?: string;
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

function authHeader(): Record<string, string> {
  const { data: session } = useAuth();
  const token = session.value?.accessToken;
  if (!token) throw new Error("Not authenticated");
  return { Authorization: `Bearer ${token}` };
}

async function parseProblemDetail(response: Response): Promise<string> {
  const bodyText = await response.text().catch(() => "");
  try {
    const parsed = JSON.parse(bodyText);
    if (parsed && typeof parsed.detail === "string") return parsed.detail;
    if (parsed && typeof parsed.title === "string") return parsed.title;
  } catch {
    // ignore
  }
  return `HTTP ${response.status}${bodyText ? ": " + bodyText.slice(0, 200) : ""}`;
}

export function useOntologyBundles() {
  const bundles = ref<OntologyBundleIO[]>([]);
  const isLoading = ref(false);
  const isActing = ref(false);
  const fetchError = ref<string | null>(null);
  const actionError = ref<string | null>(null);

  async function refresh() {
    isLoading.value = true;
    fetchError.value = null;
    try {
      const response = await fetch(
        `${v2BaseUrl()}/v2/admin/semantic/ontologies`,
        {
          headers: {
            ...authHeader(),
            Accept: "application/json",
          },
        },
      );
      if (!response.ok) {
        fetchError.value = await parseProblemDetail(response);
        handleError(fetchError.value, "listOntologyBundles");
        return;
      }
      const data = (await response.json()) as OntologyBundleListIO;
      bundles.value = data.items ?? [];
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Network error";
      fetchError.value = msg;
      handleError(e, "listOntologyBundles");
    } finally {
      isLoading.value = false;
    }
  }

  async function setEnabled(bundleId: string, enabled: boolean): Promise<void> {
    isActing.value = true;
    actionError.value = null;
    const action = enabled ? "enable" : "disable";
    try {
      const response = await fetch(
        `${v2BaseUrl()}/v2/admin/semantic/ontologies/${encodeURIComponent(bundleId)}/${action}`,
        {
          method: "POST",
          headers: {
            ...authHeader(),
            Accept: "application/json",
          },
        },
      );
      if (!response.ok) {
        const detail = await parseProblemDetail(response);
        actionError.value = detail;
        throw new Error(detail);
      }
      const updated = (await response.json()) as OntologyBundleIO;
      bundles.value = bundles.value.map(b =>
        b.id === bundleId ? updated : b,
      );
    } catch (e) {
      if (!actionError.value) {
        actionError.value =
          e instanceof Error ? e.message : `Failed to ${action} bundle`;
      }
      handleError(e, `${action}OntologyBundle`);
      throw e;
    } finally {
      isActing.value = false;
    }
  }

  async function uploadBundle(
    file: File,
    meta: UploadOntologyMetadata,
  ): Promise<void> {
    isActing.value = true;
    actionError.value = null;
    try {
      const form = new FormData();
      form.append("file", file);
      form.append("metadata", JSON.stringify(meta));
      // Intentionally no Content-Type header — browser sets multipart boundary.
      const response = await fetch(
        `${v2BaseUrl()}/v2/admin/semantic/ontologies`,
        {
          method: "POST",
          headers: {
            ...authHeader(),
            Accept: "application/json",
          },
          body: form,
        },
      );
      if (!response.ok) {
        const detail = await parseProblemDetail(response);
        actionError.value = detail;
        throw new Error(detail);
      }
      await refresh();
    } catch (e) {
      if (!actionError.value) {
        actionError.value =
          e instanceof Error ? e.message : "Failed to upload bundle";
      }
      handleError(e, "uploadOntologyBundle");
      throw e;
    } finally {
      isActing.value = false;
    }
  }

  async function deleteBundle(bundleId: string): Promise<void> {
    isActing.value = true;
    actionError.value = null;
    try {
      const response = await fetch(
        `${v2BaseUrl()}/v2/admin/semantic/ontologies/${encodeURIComponent(bundleId)}`,
        {
          method: "DELETE",
          headers: {
            ...authHeader(),
            Accept: "application/json",
          },
        },
      );
      if (!response.ok) {
        const detail = await parseProblemDetail(response);
        actionError.value = detail;
        throw new Error(detail);
      }
      bundles.value = bundles.value.filter(b => b.id !== bundleId);
    } catch (e) {
      if (!actionError.value) {
        actionError.value =
          e instanceof Error ? e.message : "Failed to delete bundle";
      }
      handleError(e, "deleteOntologyBundle");
      throw e;
    } finally {
      isActing.value = false;
    }
  }

  refresh();

  return {
    bundles,
    isLoading,
    isActing,
    fetchError,
    actionError,
    refresh,
    setEnabled,
    uploadBundle,
    deleteBundle,
  };
}
