/**
 * G1 (aidocs/16) — create / patch / delete GitReferences via the unified
 * `/v2/references?kind=git` surface.
 *
 * V2-SWEEP-001-CLIENT-REGEN: migrated off the removed generated
 * `GitReferenceApi` (whose typed `createGitReference` / `patchGitReference` /
 * `deleteGitReference` hit the plugin-specific
 * `/v2/data-objects/{appId}/git-references` path). That class is gone from the
 * regenerated client; git references are now a `kind=git` entry on the unified
 * references surface.
 *
 * Mutations stay on a bearer-token `fetch` against `/v2/references` rather than
 * the generated `ReferencesApi.createReference` / `patchReference`: the
 * regenerated client serializes the create/patch body through `JsonNodeToJSON`,
 * which only emits Jackson `JsonNode` bean metadata (empty/valueNode/…) and
 * drops the arbitrary `{repoUrl, ref, path}` payload. Until the spec exposes a
 * typed git-reference body (tracked alongside V2-SWEEP-001-CLIENT-REGEN), the
 * raw shim is the correct surface — it sends the real payload. The READ path
 * (`useFetchGitReferences`) is already typed via `ReferencesApi.listReferences`
 * because `ReferenceV2.payload` deserializes as a passthrough map.
 */
import type {
  CreateGitReferenceIO,
  GitReferenceIO,
  PatchGitReferenceIO,
} from "./gitReferenceTypes";

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

interface ReferenceV2Shape {
  appId: string;
  payload?: Record<string, unknown>;
}

function toGitReferenceIO(r: ReferenceV2Shape): GitReferenceIO {
  const p = r.payload ?? {};
  return {
    appId: r.appId,
    repoUrl: (p.repoUrl as string) ?? "",
    ref: (p.ref as string | undefined) ?? undefined,
    path: (p.path as string | undefined) ?? undefined,
  };
}

export function useManageGitReferences() {
  const isSaving = ref(false);
  const saveError = ref<string | null>(null);

  async function create(
    dataObjectAppId: string,
    body: CreateGitReferenceIO,
  ): Promise<GitReferenceIO | null> {
    isSaving.value = true;
    saveError.value = null;
    try {
      const url =
        `${v2BaseUrl()}/v2/references` +
        `?kind=git&dataObjectAppId=${encodeURIComponent(dataObjectAppId)}`;
      const res = await fetch(url, {
        method: "POST",
        headers: { ...authHeaders(), "Content-Type": "application/json" },
        body: JSON.stringify({
          repoUrl: body.repoUrl,
          ref: body.ref,
          path: body.path,
        }),
      });
      if (!res.ok) throw new Error(`createGitReference failed: ${res.status}`);
      return toGitReferenceIO((await res.json()) as ReferenceV2Shape);
    } catch (error) {
      handleError(error, "createGitReference");
      saveError.value = "Failed to create git reference";
      return null;
    } finally {
      isSaving.value = false;
    }
  }

  async function patch(
    _dataObjectAppId: string,
    appId: string,
    body: PatchGitReferenceIO,
  ): Promise<GitReferenceIO | null> {
    isSaving.value = true;
    saveError.value = null;
    try {
      const url = `${v2BaseUrl()}/v2/references/${encodeURIComponent(appId)}`;
      const res = await fetch(url, {
        method: "PATCH",
        headers: { ...authHeaders(), "Content-Type": "application/json" },
        body: JSON.stringify({
          repoUrl: body.repoUrl,
          ref: body.ref,
          path: body.path,
        }),
      });
      if (!res.ok) throw new Error(`patchGitReference failed: ${res.status}`);
      return toGitReferenceIO((await res.json()) as ReferenceV2Shape);
    } catch (error) {
      handleError(error, "patchGitReference");
      saveError.value = "Failed to update git reference";
      return null;
    } finally {
      isSaving.value = false;
    }
  }

  async function remove(
    _dataObjectAppId: string,
    appId: string,
  ): Promise<boolean> {
    isSaving.value = true;
    saveError.value = null;
    try {
      const url = `${v2BaseUrl()}/v2/references/${encodeURIComponent(appId)}`;
      const res = await fetch(url, {
        method: "DELETE",
        headers: { ...authHeaders() },
      });
      if (!res.ok) throw new Error(`deleteGitReference failed: ${res.status}`);
      return true;
    } catch (error) {
      handleError(error, "deleteGitReference");
      saveError.value = "Failed to delete git reference";
      return false;
    } finally {
      isSaving.value = false;
    }
  }

  return { create, patch, remove, isSaving, saveError };
}
