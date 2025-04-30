<script setup lang="ts">
import {
  HealthCheckStatusEnum,
  HealthzApi,
  VersionzApi,
  type HealthResponse,
  type ResponseError,
} from "@dlr-shepard/backend-client";
import { AboutItems } from "~/components/context/about/aboutItems";

useHead({
  title: "About | shepard",
});

const currentPage = ref<AboutItems>(AboutItems.VERSION);
const backendURL = useRuntimeConfig().public.backendApiUrl;
const applicationVersion = ref<string>();
const health = ref<HealthResponse | undefined>();
const healthy = ref<boolean | undefined>();

async function fetchApplicationVersion() {
  createApiInstance(VersionzApi)
    .getShepardVersion()
    .then(result => {
      applicationVersion.value = result.version;
    })
    .catch(e => {
      handleError(e, "fetching backend version");
    });
}

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
fetchApplicationVersion();
</script>

<template>
  <v-container
    fluid
    class="bg-treeview pa-0 fill-height align-start overflow-x-auto"
  >
    <v-row no-gutters class="fill-height">
      <v-col cols="3">
        <AboutSidebar v-model:model-value="currentPage" />
      </v-col>
      <v-col cols="8" class="pa-8">
        <v-container class="bg-canvas fill-height align-start">
          <div class="pt-6 pl-8">
            <div v-if="currentPage === AboutItems.VERSION">
              <v-row class="text-textbody1 text-h4 pb-3">
                {{ AboutItems.VERSION }}
              </v-row>
              <v-list>
                <v-list-item>
                  <strong>Application Version:</strong>
                  <code>{{ applicationVersion }}</code>
                </v-list-item>
                <v-list-item>
                  <strong>Backend URL:</strong>
                  <code>{{ backendURL }}</code>
                </v-list-item>
              </v-list>
            </div>
            <div v-else-if="currentPage === AboutItems.HEALTH">
              <v-row class="text-textbody1 text-h4 pb-4">
                {{ AboutItems.HEALTH }}
              </v-row>
              <v-alert
                :type="healthy ? 'success' : 'error'"
                :text="healthy ? 'Healthy' : 'Unhealthy'"
              />
              <v-list v-if="health">
                <v-list-item v-for="check in health.checks" :key="check.name">
                  <strong>{{ check.name }}</strong>
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
            <div v-else-if="currentPage === AboutItems.DOCUMENTATION">
              <v-row class="text-textbody1 text-h4">
                {{ AboutItems.DOCUMENTATION }}
              </v-row>
              <v-row>https://gitlab.com/dlr-shepard/shepard/-/wikis/home</v-row>
            </div>
          </div>
        </v-container>
      </v-col>
    </v-row>
  </v-container>
</template>
