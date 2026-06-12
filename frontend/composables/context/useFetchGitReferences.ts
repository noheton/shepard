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
import { ReferencesApi, type ReferenceV2 } from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";
import type { GitReferenceIO } from "./gitReferenceTypes";

function toGitReferenceIO(r: ReferenceV2): GitReferenceIO {
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
      const raw = await useV2ShepardApi(ReferencesApi).value.listReferences({
        kind: "git",
        dataObjectAppId,
      });
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
