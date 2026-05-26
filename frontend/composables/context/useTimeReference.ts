/**
 * TM1a — composable for reading and patching the time-reference fields
 * on a TimeseriesReference.
 *
 * Wire endpoint: PATCH /v2/timeseries-references/{appId}
 *   (Content-Type: application/merge-patch+json)
 *
 * The TimeseriesReference is already fetched by the caller; we receive it
 * as a reactive input so the panel can react to navigation changes.
 *
 * Returns:
 *   saving (ref)      — true during the PATCH call.
 *   save(patch)       — sends the merge-patch; emits success/error toast.
 */

import type { TimeReferenceV2Patch } from "@dlr-shepard/backend-client";
import { TimeseriesReferenceV2Api } from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";

export function useTimeReference() {
  const saving = ref(false);

  async function save(
    appId: string,
    patch: TimeReferenceV2Patch,
  ): Promise<boolean> {
    saving.value = true;
    try {
      await useV2ShepardApi(TimeseriesReferenceV2Api).value.patchTimeReference({
        appId,
        body: patch,
      });
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
