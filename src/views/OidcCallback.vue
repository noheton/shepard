<script setup lang="ts">
import { createVuexHelpers } from "vue2-helpers";
import { useRouter } from "vue2-helpers/vue-router";

const router = useRouter();

const { useActions } = createVuexHelpers();
const oidcStoreActions = useActions("oidcStore", ["oidcSignInCallback"]);
const callback: () => Promise<string> = oidcStoreActions.oidcSignInCallback;
callback()
  .then(redirectPath => {
    router.push(redirectPath);
  })
  .catch(err => {
    console.error(err);
    router.push("/oidc-callback-error");
  });
</script>

<template>
  <div></div>
</template>
