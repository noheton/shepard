import type { ReferenceV2, ResponseError } from "@dlr-shepard/backend-client";
import { ReferencesApi } from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";

/**
 * UX612-C1 — load any reference by its v2 appId route param.
 *
 * `GET /v2/references/{appId}` returns the unified, kind-agnostic
 * ReferenceV2 wire shape (kind + per-kind payload + the numeric `.id`).
 * Detail pages whose route param is the appId (the frontend-v2-only rule)
 * load through this composable first; any still-v1-only sub-call then
 * resolves its numeric id from the loaded entity's `.id` at call time —
 * never from the route param.
 *
 * Must be called during the synchronous setup phase (useV2ShepardApi →
 * useAuth → inject()).
 */
export function useFetchReferenceV2(
  appIdInput: MaybeRefOrGetter<string | undefined>,
) {
  const api = useV2ShepardApi(ReferencesApi);

  const referenceV2 = ref<ReferenceV2 | undefined>(undefined);
  // 404 → render `EntityNotFound`, not an eternal spinner (UU1 pattern).
  const notFound = ref<boolean>(false);

  async function fetchReference(appId: string) {
    notFound.value = false;
    try {
      referenceV2.value = await api.value.getReference({ appId });
    } catch (e) {
      referenceV2.value = undefined;
      if ((e as ResponseError)?.response?.status === 404) {
        notFound.value = true;
        return;
      }
      handleError(e as ResponseError, "fetching reference");
    }
  }

  function refresh() {
    const appId = toValue(appIdInput);
    if (appId) void fetchReference(appId);
  }

  watch(
    () => toValue(appIdInput),
    appId => {
      if (appId) void fetchReference(appId);
    },
    { immediate: true },
  );

  return { referenceV2, notFound, refresh };
}
