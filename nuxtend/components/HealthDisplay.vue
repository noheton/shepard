<script setup lang="ts">
import type { Healthz, ResponseError } from "@dlr-shepard/backend-client";
import { HealthzApi } from "@dlr-shepard/backend-client";

const health = ref<Healthz | undefined>();
const healthy = ref<boolean | undefined>();

function fetchHealthz() {
  createApiInstance(HealthzApi)
    .getServerHealth()
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
  <div>{{ health }} {{ healthy }}</div>
</template>
