<script setup lang="ts">
import { computed, type ComputedRef } from "vue";
import { createVuexHelpers } from "vue2-helpers";

const { useGetters } = createVuexHelpers();
const oidcGetters = useGetters("oidcStore", [
  "oidcIsAuthenticated",
  "oidcUser",
]);
const fullName = computed(() => {
  const user: ComputedRef<
    | {
        family_name: string;
        given_name: string;
      }
    | undefined
  > = oidcGetters.oidcUser;
  if (user.value) return user.value.family_name + ", " + user.value.given_name;
  else return "";
});
</script>

<template>
  <b-navbar toggleable="lg" variant="info" type="dark">
    <b-navbar-brand class="ml-3" to="/">
      <img src="@/assets/Shepard_Logo_1024x191.png" height="25" alt="shepard" />
    </b-navbar-brand>
    <b-navbar-toggle target="nav-collapse"></b-navbar-toggle>
    <b-collapse id="nav-collapse" is-nav>
      <b-navbar-nav id="nav">
        <b-nav-item to="/explore">Collections</b-nav-item>
        <b-nav-item-dropdown text="Containers">
          <b-dropdown-item to="/files">Files</b-dropdown-item>
          <b-dropdown-item to="/structureddata">StructuredData</b-dropdown-item>
          <b-dropdown-item to="/timeseries">Timeseries</b-dropdown-item>
          <b-dropdown-item to="/semanticrepositories">
            Semantic Repositories
          </b-dropdown-item>
        </b-nav-item-dropdown>
        <b-nav-item to="/usergroups">User Groups</b-nav-item>
        <b-nav-item to="/search">Search</b-nav-item>
        <b-nav-item to="/user">User</b-nav-item>
        <b-nav-item to="/about">About</b-nav-item>
      </b-navbar-nav>
      <b-navbar-nav class="ml-auto">
        <b-nav-item v-if="oidcGetters.oidcIsAuthenticated" to="/about-user">
          Signed in as {{ fullName }}
        </b-nav-item>
      </b-navbar-nav>
    </b-collapse>
  </b-navbar>
</template>
