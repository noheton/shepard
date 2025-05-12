import publicEndpoints from "./publicEndpoints";

export default defineNuxtRouteMiddleware(async to => {
  const { status, signIn, data, getSession } = useAuth();

  // don't allow signed in users with a valid session to access the signIn page
  if (
    to.path === "/auth/signIn" &&
    status.value === "authenticated" &&
    !data.value?.error
  ) {
    return navigateTo("/");
  }

  // allow some endpoints without authentication
  // routes starting with /_nuxt/ provide components in dev mode and should always be accessible
  if (publicEndpoints.includes(to.path) || to.path.startsWith("/_nuxt/")) {
    return;
  }

  // do not save baseUrl as redirect-url cookie
  // /auth/signIn and /_nuxt/ routes are not saved as cookie values here, since they are returned in the check before
  // Setting this cookie is workaround to a problem that is further explained here: https://gitlab.com/dlr-shepard/shepard/-/issues/399
  if (to.path != "/") {
    const redirectCookie = useCookie(signInRedirectCookie, {
      sameSite: "strict",
    });
    redirectCookie.value = to.fullPath;
  }

  //to refresh the session even when the user stays inactive for long
  //only results in a call to the OIDC provider if the token is expired
  await getSession();

  // this handles the case that the user has an authenticated session, but the session contains an error
  if (data.value?.error) {
    return signIn(undefined, {
      callbackUrl: to.fullPath,
    }) as ReturnType<typeof navigateTo>;
  }

  // Return immediately if user is already authenticated
  // this check needs to be done after the session error check
  if (status.value === "authenticated") {
    return;
  }

  return signIn(undefined, {
    callbackUrl: to.fullPath,
  }) as ReturnType<typeof navigateTo>;
});
