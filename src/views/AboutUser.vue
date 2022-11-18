<script setup lang="ts">
import { useClipboard, useTitle } from "@vueuse/core";
import { BButton, BLink, BListGroup, BListGroupItem } from "bootstrap-vue";
import type { User } from "oidc-client";
import { computed, onMounted } from "vue";
import { createVuexHelpers } from "vue2-helpers";

const { useGetters, useActions } = createVuexHelpers();
const oidcGetters = useGetters("oidcStore", [
  "oidcAccessToken",
  "oidcIsAuthenticated",
  "oidcUser",
]);
const oidcActions = useActions("oidcStore", ["signOutOidc"]);
const oidcAccessToken = computed<string | undefined>(() => {
  return oidcGetters.oidcAccessToken.value;
});

const claims = computed<
  {
    key: string;
    value: string;
  }[]
>(() => {
  if (oidcGetters.oidcIsAuthenticated.value) {
    return Object.entries(oidcGetters.oidcUser.value as User).map(entry => ({
      key: entry[0],
      value: entry[1],
    }));
  }
  return [];
});

function copyBearerToken() {
  if (oidcAccessToken.value) useClipboard().copy(oidcAccessToken.value);
}

onMounted(() => {
  useTitle("About User | shepard");
});
</script>

<template>
  <div class="about-user">
    <div class="component">
      <h4>Your current user</h4>
      <b-list-group>
        <b-list-group-item v-for="c in claims" :key="c.key">
          <strong>{{ c.key }}:</strong> {{ c.value }}
        </b-list-group-item>
        <b-list-group-item>
          <strong>Bearer Token: </strong>
          <code v-if="oidcAccessToken">
            {{ oidcAccessToken.substring(0, 50) + "... " }}
          </code>
          <b-link
            title="Copy Bearer Token"
            class="float-right"
            @click="copyBearerToken()"
          >
            <CopyIcon />
          </b-link>
        </b-list-group-item>
      </b-list-group>
    </div>
    <b-button
      class="float-right"
      variant="danger"
      @click="oidcActions.signOutOidc"
    >
      Sign out
    </b-button>
  </div>
</template>
