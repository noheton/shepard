/**
 * APISIMP-KIND-DISCRIMINATOR-2 — two-step singleton FileReference upload.
 *
 * Replaces the retired multipart `POST /v2/files` with the Option-C pattern:
 *   Step 1: POST /v2/references?kind=file&dataObjectAppId=<doAppId>
 *           body: JSON {"name": "..."}
 *           → 201 with ReferenceV2IO (contains appId)
 *   Step 2: PUT /v2/references/<appId>/content?filename=<original-name>
 *           Content-Type: application/octet-stream
 *           → 200 with updated ReferenceV2IO
 *
 * Caller usage:
 *   const { createSingleton } = useCreateSingletonFileReference();
 *   const result = await createSingleton({
 *     parentDataObjectAppId: do.appId,
 *     name: "kr210-r2700-urdf",
 *     file: theFileFromInputElement,
 *   });
 *   // result.appId resolves directly to bytes via /v2/files/{appId}/content
 */

export interface CreateSingletonFileRequest {
  parentDataObjectAppId: string;
  name: string;
  file: File;
}

export interface CreatedSingletonFile {
  appId: string;
  name: string;
  createdAt: string;
  createdBy: string;
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

function httpErrorDetail(status: number, text: string): string {
  if (status === 400) return "Bad request — check parent DataObject appId and file.";
  if (status === 401) return "Not authenticated — sign in again.";
  if (status === 403) return "Not authorised on this DataObject.";
  return `HTTP ${status}` + (text ? `: ${text}` : "");
}

export function useCreateSingletonFileReference() {
  const loading = ref<boolean>(false);
  const error = ref<string | null>(null);

  async function createSingleton(
    req: CreateSingletonFileRequest,
  ): Promise<CreatedSingletonFile | null> {
    loading.value = true;
    error.value = null;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      const base = v2BaseUrl();

      // Step 1: create metadata node
      const createQs = new URLSearchParams({
        kind: "file",
        dataObjectAppId: req.parentDataObjectAppId,
      }).toString();
      const createResp = await fetch(`${base}/v2/references?${createQs}`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json",
          Accept: "application/json",
        },
        body: JSON.stringify({ name: req.name }),
      });
      if (!createResp.ok) {
        const txt = await createResp.text().catch(() => "");
        const detail = httpErrorDetail(createResp.status, txt);
        error.value = detail;
        handleError(new Error(detail), "createSingletonFileReference/step1");
        return null;
      }
      const meta = (await createResp.json()) as CreatedSingletonFile;
      const refAppId = meta.appId;

      // Step 2: upload bytes
      const uploadQs = new URLSearchParams({ filename: req.file.name }).toString();
      const uploadResp = await fetch(
        `${base}/v2/references/${encodeURIComponent(refAppId)}/content?${uploadQs}`,
        {
          method: "PUT",
          headers: {
            Authorization: `Bearer ${accessToken}`,
            "Content-Type": "application/octet-stream",
            Accept: "application/json",
          },
          body: req.file,
        },
      );
      if (!uploadResp.ok) {
        const txt = await uploadResp.text().catch(() => "");
        const detail = httpErrorDetail(uploadResp.status, txt);
        error.value = detail;
        handleError(new Error(detail), "createSingletonFileReference/step2");
        return null;
      }
      const result = (await uploadResp.json()) as CreatedSingletonFile;
      emitSuccess(`Uploaded "${req.name}" as a singleton FileReference.`);
      return result;
    } catch (e) {
      error.value = (e as Error).message ?? "Singleton upload failed";
      handleError(e, "createSingletonFileReference");
      return null;
    } finally {
      loading.value = false;
    }
  }

  return { createSingleton, loading, error };
}
