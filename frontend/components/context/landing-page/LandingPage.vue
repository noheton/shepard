<script setup lang="ts">
import { useTheme } from "vuetify";

const theme = useTheme();
const isDarkMode = computed(() => theme.global.current.value.dark);

// Quarkus Swagger UI lives on the backend, not the frontend domain.
// Build the URL from backendApiUrl (e.g. https://shepard-api.example/shepard/api
// → https://shepard-api.example/shepard/doc/swagger-ui/). Trailing slash matters.
const { public: publicConfig } = useRuntimeConfig();
const apiDocsUrl = computed(() => {
  const base = (publicConfig.backendApiUrl as string) || "";
  if (!base) return "/shepard/doc/swagger-ui/";
  return base.replace(/\/shepard\/api\/?$/, "") + "/shepard/doc/swagger-ui/";
});

const dlrLogoSrc = computed(() =>
  isDarkMode.value
    ? new URL("../../../assets/dlr_logo_white.svg", import.meta.url).href
    : new URL("../../../assets/dlr_logo.svg", import.meta.url).href,
);

const containerIconSrc = computed(() =>
  isDarkMode.value
    ? new URL("../../../assets/container_icon_dark.svg", import.meta.url).href
    : new URL("../../../assets/container_icon.svg", import.meta.url).href,
);
const collectionIconSrc = computed(() =>
  isDarkMode.value
    ? new URL("../../../assets/collection_icon_dark.svg", import.meta.url).href
    : new URL("../../../assets/collection_icon.svg", import.meta.url).href,
);
const dataObjectIconSrc = computed(() =>
  isDarkMode.value
    ? new URL("../../../assets/data_object_icon_dark.svg", import.meta.url)
        .href
    : new URL("../../../assets/data_object_icon.svg", import.meta.url).href,
);
</script>

<template>
  <div>
    <!-- Hero -->
    <v-container fluid class="bg-divider2 py-10">
      <v-col cols="12" class="d-flex flex-column align-center justify-center">
        <v-img
          src="../../../assets/shepard_logo.svg"
          height="120"
          width="465"
          class="mb-3"
        />
        <div class="text-subtitle-1">
          Storage for Heterogeneous Product and Research Data
        </div>
        <!-- DLR institutional attribution, subordinate to the shepard wordmark. -->
        <a
          href="https://www.dlr.de"
          target="_blank"
          rel="external noopener"
          class="dlr-credit mt-4 d-flex align-center"
          title="shepard is developed by DLR"
        >
          <span class="text-caption text-medium-emphasis mr-2">Developed by</span>
          <v-img
            :src="dlrLogoSrc"
            height="22"
            width="74"
            alt="Deutsches Zentrum für Luft- und Raumfahrt"
          />
        </a>
      </v-col>
    </v-container>

    <!-- Primary CTAs -->
    <v-container class="py-12">
      <v-row justify="center" class="ga-6">
        <v-col cols="12" sm="auto">
          <LandingPageCard
            title="Collections"
            icon-type="collection"
            content-title="Organise your projects"
            content="Group related data objects into a collection — one per project, experiment, or thematic area. Add context with metadata, annotations, and a lab journal."
            button-text="Go to Collections"
            button-link="/collections"
          />
        </v-col>
        <v-col cols="12" sm="auto">
          <LandingPageCard
            title="Containers"
            icon-type="container"
            content-title="Store your raw data"
            content="Upload and browse timeseries, files, spatial data, and more. Containers hold the raw payloads; data objects in collections give them meaning."
            button-text="Go to Containers"
            button-link="/containers"
          />
        </v-col>
      </v-row>
    </v-container>

    <!-- How it works — 3-column overview -->
    <v-container fluid class="bg-divider2 py-12">
      <v-row justify="center" class="mb-6">
        <div class="text-h4 text-semibold">How it works</div>
      </v-row>
      <v-row justify="center">
        <v-col
          cols="12"
          sm="4"
          md="3"
          class="d-flex flex-column align-center text-center px-8"
        >
          <v-img
            :src="containerIconSrc"
            width="64"
            height="64"
            class="mb-4 flex-grow-0"
          />
          <div class="text-subtitle-1 text-semibold mb-2">
            1 — Store raw data
          </div>
          <div class="text-body-2">
            Containers hold typed payloads — timeseries, files, spatial data,
            structured records, and more — without requiring a fixed schema up
            front.
          </div>
        </v-col>
        <v-col
          cols="12"
          sm="4"
          md="3"
          class="d-flex flex-column align-center text-center px-8"
        >
          <v-img
            :src="dataObjectIconSrc"
            width="64"
            height="64"
            class="mb-4 flex-grow-0"
          />
          <div class="text-subtitle-1 text-semibold mb-2">
            2 — Describe with data objects
          </div>
          <div class="text-body-2">
            Data objects link raw container data with semantic annotations,
            attributes, and lab journal entries so every dataset carries its
            context.
          </div>
        </v-col>
        <v-col
          cols="12"
          sm="4"
          md="3"
          class="d-flex flex-column align-center text-center px-8"
        >
          <v-img
            :src="collectionIconSrc"
            width="64"
            height="64"
            class="mb-4 flex-grow-0"
          />
          <div class="text-subtitle-1 text-semibold mb-2">
            3 — Organise in collections
          </div>
          <div class="text-body-2">
            Collections group related data objects into projects. Use
            predecessor/successor and parent/child relationships to model
            process steps and assemblies.
          </div>
        </v-col>
      </v-row>
    </v-container>

    <!-- Documentation card -->
    <v-container
      fluid
      class="d-flex justify-center align-center bg-canvas py-12"
      max-width="815"
    >
      <v-card color="canvas">
        <template #title>
          <div class="text-h3 text-semibold">Shepard Documentation</div>
        </template>
        <template #text>
          <div class="text-body-1">
            Automate your data storage by working with the API directly. Check
            out the documentation to learn about all capabilities.
          </div>
        </template>
        <template #actions>
          <div class="d-flex justify-end gap-3 w-100">
            <v-btn
              variant="outlined"
              :href="apiDocsUrl"
              target="_blank"
            >
              API Docs
            </v-btn>
            <v-btn
              class="bg-primary text-canvas"
              variant="flat"
              href="https://gitlab.com/dlr-shepard/shepard/-/wikis/home"
              target="_blank"
            >
              Shepard Documentation
            </v-btn>
          </div>
        </template>
      </v-card>
    </v-container>
  </div>
</template>

<style lang="scss" scoped>
.dlr-credit {
  text-decoration: none;
  opacity: 0.9;
  transition: opacity 0.15s ease;
  &:hover {
    opacity: 1;
  }
}
</style>
