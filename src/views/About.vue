<script setup lang="ts">
import Loading from "@/components/generic/Loading.vue";
import HealthzService from "@/services/healthzService";
import getEnv from "@/utils/env";
import { emitter } from "@/utils/event-bus";
import type { Healthz } from "@dlr-shepard/shepard-client";
import { version as clientVersion } from "@dlr-shepard/shepard-client/package.json";
import { onMounted, ref, type Ref } from "vue";
import { version as appVersion } from "../../package.json";

const backendUrl = getEnv("VUE_APP_BACKEND");
const backend: Ref<{ info: { version: string } } | undefined> = ref();
const health: Ref<Healthz | undefined> = ref();

function fetchBackend() {
  const openapiURL = backendUrl.split("/");
  openapiURL.pop();
  openapiURL.push("doc/openapi.json");
  fetch(openapiURL.join("/"))
    .then(response => response.json())
    .then(data => (backend.value = data));
}

function fetchHealtz() {
  HealthzService.getServerHealth()
    .then(response => {
      health.value = response;
    })
    .catch(e => {
      const error = "Error while fetching server health: " + e.statusText;
      console.log(error);
      emitter.emit("error", error);
    });
}

onMounted(() => {
  fetchBackend();
  fetchHealtz();
});
</script>

<template>
  <div class="about">
    <div class="component">
      <h4>About shepard</h4>
      <b-list-group class="mb-3">
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
      <b-list-group v-if="health" class="mb-3">
        <b-list-group-item v-for="(value, key) in health" :key="key">
          <strong>{{ key }}:</strong>
          <div class="float-right">
            <HealthyIcon
              v-if="value == 'healthy'"
              v-b-tooltip.hover
              title="healthy"
              variant="success"
            />
            <UnhealthyIcon
              v-else
              v-b-tooltip.hover
              title="Unhealthy"
              variant="danger"
            />
          </div>
        </b-list-group-item>
      </b-list-group>
      <Loading v-else />
    </div>
  </div>
</template>
