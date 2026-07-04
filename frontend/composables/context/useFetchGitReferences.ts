/**
 * G1 (aidocs/16) — fetch GitReferences for a DataObject via the unified
 * `/v2/references?kind=git` surface.
 *
 * PLUGIN-REF-HANDLER-FE-REPOINT: migrated from the generated
 * `GitReferenceApi.listGitReferences(...)` call (which hit the plugin-specific
 * `/v2/data-objects/{appId}/git-references` path) to the unified
 * `GET /v2/references?kind=git&dataObjectAppId={appId}` endpoint now that the
 * `git` ReferenceKindHandler is installed (merged in bfab5f04b).
 *
 * V2-SWEEP-001-CLIENT-REGEN: the raw `fetch` shim is now a typed call through
 * `ReferencesApi.listReferences({ kind: "git", dataObjectAppId })` — the
 * regenerated client carries the unified `?kind=` operations. The per-kind
 * `payload` map (repoUrl, ref, path, mode) is mapped back to the local
 * `GitReferenceIO` shape so consumers (`GitReferencesPane.vue`,
 * `dataTableElementMappingUtil.ts`) require no changes.
 *
 * Mutation ops (create / patch / delete) live in `useManageGitReferences.ts`,
 * also on the unified `ReferencesApi` surface.
 */
import type { GitReferenceIO } from "./gitReferenceTypes";

/**
 * BUG-DO-DETAIL-A-TOAST-2026-06-29 — bypass the generated `ReferencesApi`
 * client for the list call. The generated `listReferencesRaw` does
 * `jsonValue.map(ReferenceV2FromJSON)` against the response body, which
 * throws ".map is not a function" the moment the backend flips the list
 * shape to a PagedResponseIO envelope `{ items: [...] }` (the same drift
 * that just shipped on /v2/labjournal/* and triggers the toast on the
 * DataObject detail page). Regenerating the generated client is the longer
 * fix; here we do what the other v2 reference composables already do —
 * raw `fetch` + unwrap envelope client-side.
 */

interface ReferenceV2IO {
  appId?: string | null;
  payload?: Record<string, unknown> | null;
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

function toGitReferenceIO(r: ReferenceV2IO): GitReferenceIO {
  const p = r.payload ?? {};
  return {
    appId: r.appId ?? "",
    repoUrl: (p.repoUrl as string) ?? "",
    ref: (p.ref as string | undefined) ?? undefined,
    path: (p.path as string | undefined) ?? undefined,
  };
}

export function useFetchGitReferences(dataObjectAppId: string) {
  const gitReferences = ref<GitReferenceIO[]>([]);
  const isLoading = ref(false);

  async function refresh() {
    isLoading.value = true;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      const url =
        `${v2BaseUrl()}/v2/references` +
        `?kind=git&dataObjectAppId=${encodeURIComponent(dataObjectAppId)}`;
      const response = await fetch(url, {
        headers: {
          ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
          Accept: "application/json",
        },
      });
      if (response.status === 404 || response.status === 400) {
        gitReferences.value = [];
        return;
      }
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      const body = (await response.json()) as
        | ReferenceV2IO[]
        | { items?: ReferenceV2IO[] };
      const raw = Array.isArray(body)
        ? body
        : ((body as { items?: ReferenceV2IO[] }).items ?? []);
      gitReferences.value = raw.map(toGitReferenceIO);
    } catch (error) {
      handleError(error, "listGitReferences");
    } finally {
      isLoading.value = false;
    }
  }

  refresh();

  return { gitReferences, isLoading, refresh };
}
