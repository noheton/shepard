import { MeRoleInApi, type MeRoleInIO } from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";

/**
 * U1c2: Fetches the current user's role in the given Collection via
 * `GET /v2/me/role-in/{collectionAppId}`. Returns reactive refs for
 * the raw role-in payload and a human-readable role label.
 *
 * Silently swallows 401 / 403 / 404 — the chip simply stays hidden
 * if the user has no access or is not authenticated.
 */
export function useMyRoleIn(collectionAppId: Ref<string | null | undefined>) {
  const roleIn = ref<MeRoleInIO | null>(null);

  /** Highest-effective role label. Null until fetch completes or if no access. */
  const roleLabel = computed<string | null>(() => {
    if (!roleIn.value) return null;
    if (roleIn.value.manage) return "Owner";
    if (roleIn.value.write) return "Editor";
    if (roleIn.value.read) return "Reader";
    return null;
  });

  const api = useV2ShepardApi(MeRoleInApi);

  function fetch(appId: string) {
    api.value
      .getRoleIn(appId)
      .then(result => {
        roleIn.value = result;
      })
      .catch(() => {
        roleIn.value = null;
      });
  }

  // Initial fetch when appId is available.
  if (collectionAppId.value) {
    fetch(collectionAppId.value);
  }

  // Refetch when the collection changes (sidebar does not remount on navigation).
  watch(collectionAppId, newAppId => {
    roleIn.value = null;
    if (newAppId) {
      fetch(newAppId);
    }
  });

  return { roleIn, roleLabel };
}
