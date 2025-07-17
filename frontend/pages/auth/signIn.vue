<script setup lang="ts">
/*
  This sign-in component is a workaround to the problem explained in: https://gitlab.com/dlr-shepard/shepard/-/issues/399
*/
const redirectCookie = useCookie(signInRedirectCookie);

onMounted(() => {
  if (redirectCookie.value) {
    const redirectUrl = redirectCookie.value;
    redirectCookie.value = undefined;
    return navigateTo(redirectUrl, {
      external: false,
      replace: true,
      redirectCode: 302,
    });
  } else {
    console.debug(
      "signIn page could not find redirect cookie, navigating to '/'...",
    );
    return navigateTo("/", {
      external: false,
      replace: true,
      redirectCode: 302,
    });
  }
});
</script>

<template>
  <CenteredLoadingSpinner />
</template>
