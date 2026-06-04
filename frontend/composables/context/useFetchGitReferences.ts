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
 * The per-kind `payload` map carries: repoUrl, ref, path, mode.
 * These are mapped back to `GitReferenceIO` (from `@dlr-shepard/backend-client`)
 * so consumers (`GitReferencesPane.vue`, `dataTableElementMappingUtil.ts`)
 * require no changes.
 *
 * Mutation ops (create / patch / delete) are handled by
 * `useManageGitReferences.ts` and stay on the plugin path — do not touch them
 * here.
 */
import type { GitReferenceIO } from "@dlr-shepard/backend-client";

interface ReferenceV2IO {
  appId: string;
  id?: number;
  name?: string | null;
  createdAt?: string | null;
  createdBy?: string | null;
  kind: string;
  payload: Record<string, unknown>;
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
  const p = r.payload;
  return {
    appId: r.appId,
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

    const { data: session } = useAuth();
    const accessToken = session.value?.accessToken;
    if (!accessToken) {
      isLoading.value = false;
      return;
    }

    const url =
      `${v2BaseUrl()}/v2/references` +
      `?kind=git&dataObjectAppId=${encodeURIComponent(dataObjectAppId)}`;

    try {
      const response = await fetch(url, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: "application/json",
        },
      });
      if (!response.ok) {
        const bodyText = await response.text().catch(() => "");
        handleError(
          `Failed to fetch git references (HTTP ${response.status}): ${bodyText.slice(0, 200)}`,
          "listGitReferences",
        );
        return;
      }
      const raw = (await response.json()) as ReferenceV2IO[];
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
