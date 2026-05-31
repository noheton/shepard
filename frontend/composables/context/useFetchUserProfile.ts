import { UserApi, type User } from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";
import { useEffectiveRoles } from "~/composables/context/useEffectiveRoles";

/**
 * AUTH-API-CALLS-UNGATED — only fetch the profile when the nuxt-auth session
 * is authenticated. Pre-fix this composable fired on every mount (including
 * the anonymous landing page) and the resulting 401 was absorbed by the
 * `useAuthRefreshMiddleware` suppression guard (BUG-SIGNOUT-LOOP-1). The
 * suppression remains a safety net; this gate is the cleaner shape that
 * keeps the call from happening at all when there's no token.
 *
 * FE-ROLE-DUAL-SOURCE-1 — additionally hydrates the global
 * `useEffectiveRoles()` cache so role-gated UI (HeaderBar admin links,
 * `/admin/*` UnauthorizedView guards) reflects backend-resolved roles
 * including Neo4j `:HAS_ROLE` grants. Until BE-USERS-ME-ROLES-1 ships
 * the `effectiveRoles` field on the wire shape, hydration falls back to
 * the JWT claim — same behaviour as the legacy
 * `hasInstanceAdminRole(accessToken)` path.
 */
export function useFetchUserProfile() {
  const user = ref<User>();
  const isLoading = ref<boolean>(false);
  const { data, status } = useAuth();
  const { hydrateFromProfile, reset: resetRoles } = useEffectiveRoles();

  function fetchUserProfile() {
    if (status.value !== "authenticated") {
      // Sign-out / never-signed-in: clear the role cache so a subsequent
      // sign-in re-fetches rather than seeing stale roles.
      resetRoles();
      return;
    }
    isLoading.value = true;
    useShepardApi(UserApi)
      .value.getCurrentUser()
      .then(response => {
        user.value = response;
        isLoading.value = false;
        // FE-ROLE-DUAL-SOURCE-1 — hydrate the role cache from the
        // profile response. Today UserIO carries no effectiveRoles
        // field, so this falls through to the JWT-derived list.
        hydrateFromProfile(
          response as unknown as { effectiveRoles?: string[] },
          data.value?.accessToken,
        );
      })
      .catch(error => {
        isLoading.value = false;
        // FE-ROLE-DUAL-SOURCE-1 — fetch failed; still hydrate roles
        // from the JWT so the UI doesn't sit in indeterminate loading.
        hydrateFromProfile(null, data.value?.accessToken);
        handleError(error, "fetching user profile");
      });
  }

  // Fire when (and only when) the session is authenticated. `immediate: true`
  // covers the common case where the user is already signed in at mount;
  // the watcher covers a later sign-in transition without a remount.
  watch(status, () => fetchUserProfile(), { immediate: true });

  return { user, isLoading };
}
