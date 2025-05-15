<script setup lang="ts">
import {
  HealthCheckStatusEnum,
  HealthzApi,
  type HealthResponse,
  type ResponseError,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import { AboutFragments } from "./aboutMenuItems";

const health = ref<HealthResponse | undefined>();
const healthy = ref<boolean | undefined>();

function fetchHealthz() {
  useShepardApi(HealthzApi)
    .value.getServerHealth()
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

fetchHealthz();
</script>

<template>
  <div :id="AboutFragments.HEALTH" class="d-flex flex-column ga-4">
    <div class="text-h4 pb-4">Health</div>
    <v-alert
      :type="healthy ? 'success' : 'error'"
      :text="healthy ? 'Healthy' : 'Unhealthy'"
    />
    <v-list v-if="health">
      <v-list-item v-for="check in health.checks" :key="check.name">
        <strong>{{ check.name }}</strong>
        &nbsp;
        <v-icon
          v-if="check.status === HealthCheckStatusEnum.Up"
          color="success"
          icon="mdi-check-circle-outline"
        />
        <v-icon
          v-else-if="check.status === HealthCheckStatusEnum.Down"
          color="error"
          icon="mid-close-circle-outline"
        />
      </v-list-item>
    </v-list>
  </div>
</template>
