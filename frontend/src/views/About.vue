<script setup lang="ts">
import Loading from "@/components/generic/Loading.vue";
import HealthzService from "@/services/healthzService";
import getEnv from "@/utils/env";
import {
  HealthCheckStatusEnum,
  type HealthResponse,
  type ResponseError,
} from "@dlr-shepard/backend-client";
import { useTitle } from "@vueuse/core";
import { onMounted, ref } from "vue";

const backendUrl = getEnv("VITE_BACKEND");
const backend = ref<{ info: { version: string } } | undefined>();
const health = ref<HealthResponse | undefined>();
const healthy = ref(true);
const appVersion = APP_VERSION;

function fetchBackend() {
  const openapiURL = backendUrl.split("/");
  openapiURL.pop();
  openapiURL.push("doc/openapi.json");
  fetch(openapiURL.join("/"))
    .then(response => response.json())
    .then(data => (backend.value = data));
}

function fetchHealthz() {
  HealthzService.getServerHealth()
    .then(response => {
      health.value = response;
      healthy.value = true;
    })
    .catch(async e => {
      healthy.value = false;
      const result = await (e as ResponseError).response.body
        ?.getReader()
        .read();
      if (result?.value) {
        const errorString = new TextDecoder().decode(result.value);
        health.value = JSON.parse(errorString);
      }
    });
}

onMounted(() => {
  fetchBackend();
  fetchHealthz();
  useTitle("About | shepard");
});
</script>

<template>
  <div class="view">
    <h4 class="title">About shepard</h4>
    <b-list-group class="mb-3">
      <b-list-group-item>
        <strong>Frontend Version:</strong>
        <code> {{ appVersion }} </code>
      </b-list-group-item>
      <b-list-group-item>
        <strong>Client Version:</strong>
        <!-- TODO: What to show here? -->
        <code> TBD </code>
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

    <h4>System health</h4>
    <b-alert :show="healthy" variant="success"> Healthy </b-alert>
    <b-alert :show="!healthy" variant="danger"> Unhealthy </b-alert>
    <b-list-group v-if="health" class="mb-3">
      <b-list-group-item v-for="check in health.checks" :key="check.name">
        <strong>{{ check.name }}:</strong>
        <div class="float-right">
          <HealthyIcon
            v-if="check.status == HealthCheckStatusEnum.Up"
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
</template>
