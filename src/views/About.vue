<template>
  <div class="about">
    <div class="component">
      <h4>About shepard</h4>
      <b-list-group>
        <b-list-group-item>
          <strong>Frontend Version:</strong>
          <code> {{ appVersion }} </code>
        </b-list-group-item>
        <b-list-group-item>
          <strong>Client Version:</strong>
          <code> {{ clientVersion }} </code>
        </b-list-group-item>
        <b-list-group-item>
          <strong>Backend Version:</strong>
          <code v-if="backend"> {{ backend.info.version }} </code>
          <code v-else> unknown </code>
        </b-list-group-item>
        <b-list-group-item>
          <strong>Backend URL:</strong>
          <code> {{ backendUrl }} </code>
        </b-list-group-item>
      </b-list-group>
    </div>
  </div>
</template>

<script lang="ts">
import getEnv from "@/utils/env";
import { version as clientVersion } from "@dlr-shepard/shepard-client/package.json";
import Vue from "vue";
import { version as appVersion } from "../../package.json";

interface AboutData {
  backend?: unknown;
  appVersion: string;
  clientVersion: string;
  backendUrl: string;
}

export default Vue.extend({
  data() {
    return {
      backend: undefined,
      appVersion: appVersion,
      clientVersion: clientVersion,
      backendUrl: getEnv("VUE_APP_BACKEND"),
    } as AboutData;
  },
  mounted() {
    this.fetchBackend();
  },
  methods: {
    fetchBackend() {
      const openapiURL = this.backendUrl.split("/");
      openapiURL.pop();
      openapiURL.push("doc/openapi.json");
      fetch(openapiURL.join("/"))
        .then(response => response.json())
        .then(data => (this.backend = data));
    },
  },
});
</script>
