import { UserApi, type User } from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";

/**
 * AUTH-API-CALLS-UNGATED — only fetch the profile when the nuxt-auth session
 * is authenticated. Pre-fix this composable fired on every mount (including
 * the anonymous landing page) and the resulting 401 was absorbed by the
 * `useAuthRefreshMiddleware` suppression guard (BUG-SIGNOUT-LOOP-1). The
 * suppression remains a safety net; this gate is the cleaner shape that
 * keeps the call from happening at all when there's no token.
 */
export function useFetchUserProfile() {
  const user = ref<User>();
  const isLoading = ref<boolean>(false);
  const { status } = useAuth();

  function fetchUserProfile() {
    if (status.value !== "authenticated") return;
    isLoading.value = true;
    useShepardApi(UserApi)
      .value.getCurrentUser()
      .then(response => {
        user.value = response;
        isLoading.value = false;
      })
      .catch(error => {
        isLoading.value = false;
        handleError(error, "fetching user profile");
      });
  }

  // Fire when (and only when) the session is authenticated. `immediate: true`
  // covers the common case where the user is already signed in at mount;
  // the watcher covers a later sign-in transition without a remount.
  watch(status, () => fetchUserProfile(), { immediate: true });

  return { user, isLoading };
}
