/**
 * TM1a — composable for patching the time-reference fields on a
 * TimeseriesReference.
 *
 * V2CONV-A2: repointed from the per-kind `PATCH /v2/timeseries-references/{appId}`
 * to the unified `PATCH /v2/references/{appId}` surface. The body shape
 * (`TimeReferenceV2Patch`: timeReference / wallClockOffset /
 * wallClockOffsetSource) is unchanged — the unified resource dispatches by the
 * entity's resolved kind to the timeseries patcher, which applies the same
 * time-alignment validation. Content-Type stays `application/merge-patch+json`.
 *
 * Implemented as a hand-written `fetch` against the new path because the
 * generated `@dlr-shepard/backend-client` does not yet carry a
 * `ReferencesV2Api` method (regenerating the client requires the OpenAPI
 * toolchain not available in this worktree — see the V2CONV-A2 report).
 *
 * Returns:
 *   saving (ref)      — true during the PATCH call.
 *   save(appId,patch) — sends the merge-patch; emits success/error toast.
 */

import type { TimeReferenceV2Patch } from "@dlr-shepard/backend-client";

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

export function useTimeReference() {
  const saving = ref(false);

  async function save(
    appId: string,
    patch: TimeReferenceV2Patch,
  ): Promise<boolean> {
    saving.value = true;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      const headers: Record<string, string> = {
        "Content-Type": "application/merge-patch+json",
        Accept: "application/json",
      };
      if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
      const resp = await fetch(
        `${v2BaseUrl()}/v2/references/${encodeURIComponent(appId)}`,
        {
          method: "PATCH",
          headers,
          body: JSON.stringify(patch),
        },
      );
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
      emitSuccess("Time reference saved.");
      return true;
    } catch (e) {
      handleError(e as Error, "saving time reference");
      return false;
    } finally {
      saving.value = false;
    }
  }

  return { saving, save };
}
