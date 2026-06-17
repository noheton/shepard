/**
 * APISIMP-STRUCTURED-DATA-KIND slice 3 — create a StructuredDataReference via
 * the unified POST /v2/references?kind=structured-data surface and optionally
 * upload an initial JSON document via PUT /v2/references/{appId}/content.
 *
 * Why a thin fetch wrapper and not the generated backend-client:
 *   - `kind=structured-data` is a v2-only route that the generated
 *     `@dlr-shepard/backend-client` does not expose
 *   - pattern mirrors `useCreateReferencesV2.ts` and `useCreateSingletonFileReference.ts`
 *
 * Two-step create:
 *   1. POST /v2/references?kind=structured-data&dataObjectAppId=<uuid>
 *      body: { name, structuredDataContainerAppId }
 *   2. (optional) PUT /v2/references/{newAppId}/content?filename=<name>.json
 *      body: raw UTF-8 JSON bytes (application/octet-stream)
 */

export interface CreateStructuredDataReferenceRequest {
  dataObjectAppId: string;
  name: string;
  containerAppId: string;
  /** Optional JSON string to upload as the initial document content. */
  jsonPayload?: string;
}

export interface CreatedStructuredDataReferenceIO {
  appId: string;
  name: string;
  kind: string;
  payload?: {
    structuredDataContainerAppId?: string | null;
    structuredDataOids?: string[];
  } | null;
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

export function useCreateStructuredDataReference() {
  const loading = ref<boolean>(false);
  const error = ref<string | null>(null);

  async function create(
    req: CreateStructuredDataReferenceRequest,
  ): Promise<CreatedStructuredDataReferenceIO | null> {
    loading.value = true;
    error.value = null;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      const authHeader = accessToken ? `Bearer ${accessToken}` : undefined;

      // ── Step 1: create the reference metadata node ────────────────────────
      const createUrl =
        `${v2BaseUrl()}/v2/references` +
        `?kind=structured-data&dataObjectAppId=${encodeURIComponent(req.dataObjectAppId)}`;

      const createRes = await fetch(createUrl, {
        method: "POST",
        headers: {
          ...(authHeader ? { Authorization: authHeader } : {}),
          "Content-Type": "application/json",
          Accept: "application/json",
        },
        body: JSON.stringify({
          name: req.name,
          structuredDataContainerAppId: req.containerAppId,
        }),
      });

      if (!createRes.ok) {
        const statusText =
          createRes.status === 400
            ? "Bad request — check DataObject appId and container appId."
            : createRes.status === 401
              ? "Not authenticated — sign in again."
              : createRes.status === 403
                ? "Not authorised on this DataObject."
                : createRes.status === 404
                  ? "DataObject or container not found."
                  : `HTTP ${createRes.status}`;
        error.value = statusText;
        handleError(new Error(statusText), "createStructuredDataReference");
        return null;
      }

      const created = (await createRes.json()) as CreatedStructuredDataReferenceIO;

      // ── Step 2 (optional): upload initial JSON document ───────────────────
      if (req.jsonPayload !== undefined && req.jsonPayload.trim().length > 0) {
        const filename = encodeURIComponent(`${req.name}.json`);
        const uploadUrl =
          `${v2BaseUrl()}/v2/references/${encodeURIComponent(created.appId)}/content` +
          `?filename=${filename}`;
        const body = new TextEncoder().encode(req.jsonPayload);

        const uploadRes = await fetch(uploadUrl, {
          method: "PUT",
          headers: {
            ...(authHeader ? { Authorization: authHeader } : {}),
            "Content-Type": "application/octet-stream",
            "Content-Length": String(body.byteLength),
          },
          body,
        });

        if (!uploadRes.ok) {
          // The reference was created; only the content upload failed.
          // Surface as a non-fatal warning so the caller still gets the appId.
          handleError(
            new Error(`Content upload failed: HTTP ${uploadRes.status}`),
            "uploadStructuredDataContent",
          );
        }
      }

      emitSuccess(`Created structured data reference "${req.name}".`);
      return created;
    } catch (e) {
      error.value = (e as Error)?.message ?? "Create structured data reference failed";
      handleError(e, "createStructuredDataReference");
      return null;
    } finally {
      loading.value = false;
    }
  }

  return { create, loading, error };
}
