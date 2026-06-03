/**
 * SINGLETON-FILE-04 — POST /v2/files (FR1b singleton) wrapper.
 *
 * One File → one :SingletonFileReference. This is the singleton-default
 * path the upload dialog picks when the user hasn't opted into the
 * multi-file bundle shape. The endpoint takes a multipart `file` part
 * plus query params `parentDataObjectAppId` + `name`, returns 201 with
 * a `FileReferenceV2IO` body shape that mirrors `useFetchSingletonFileReferences.ts`.
 *
 * Why a thin fetch wrapper and not a backend-client method:
 *   - the v2/files singleton endpoint is fork-only — the generated
 *     `@dlr-shepard/backend-client` doesn't expose it
 *   - the request shape is multipart + query-string, not the
 *     standard JSON body the generated client wires up
 *   - this matches the existing pattern in `useFetchSingletonFileReferences`
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
  dataObjectId?: number;
  createdAt: string;
  createdBy: string;
  type?: string;
  file?: {
    filename?: string;
    fileSize?: number | null;
    md5?: string;
    oid?: string;
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
      const qs = new URLSearchParams({
        parentDataObjectAppId: req.parentDataObjectAppId,
        name: req.name,
      }).toString();
      const url = `${v2BaseUrl()}/v2/files?${qs}`;
      const form = new FormData();
      form.append("file", req.file, req.file.name);
      const response = await fetch(url, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: "application/json",
          // Content-Type intentionally omitted so the browser sets the
          // multipart boundary automatically.
        },
        body: form,
      });
      if (!response.ok) {
        const txt = await response.text().catch(() => "");
        const detail =
          response.status === 400
            ? "Bad request — check parent DataObject appId and file."
            : response.status === 401
              ? "Not authenticated — sign in again."
              : response.status === 403
                ? "Not authorised on this DataObject."
                : `HTTP ${response.status}` + (txt ? `: ${txt}` : "");
        error.value = detail;
        handleError(new Error(detail), "createSingletonFileReference");
        return null;
      }
      const payload = (await response.json()) as CreatedSingletonFile;
      emitSuccess(`Uploaded "${req.name}" as a singleton FileReference.`);
      return payload;
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
