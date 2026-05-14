<script setup lang="ts">
import { useTheme } from "vuetify";

const theme = useTheme();
const isDarkMode = computed(() => theme.global.current.value.dark);

const containerIconSrc = computed(() => {
  return isDarkMode.value
    ? new URL("../../../assets/container_icon_dark.svg", import.meta.url).href
    : new URL("../../../assets/container_icon.svg", import.meta.url).href;
});
const collectionIconSrc = computed(() => {
  return isDarkMode.value
    ? new URL("../../../assets/collection_icon_dark.svg", import.meta.url).href
    : new URL("../../../assets/collection_icon.svg", import.meta.url).href;
});
const dataObjectIconSrc = computed(() => {
  return isDarkMode.value
    ? new URL("../../../assets/data_object_icon_dark.svg", import.meta.url).href
    : new URL("../../../assets/data_object_icon.svg", import.meta.url).href;
});
const dataReferenceIconSrc = computed(() => {
  return isDarkMode.value
    ? new URL("../../../assets/data_reference_icon_dark.svg", import.meta.url)
        .href
    : new URL("../../../assets/data_reference_icon.svg", import.meta.url).href;
});
const dataObjectRelationshipIconSrc = computed(() => {
  return isDarkMode.value
    ? new URL(
        "../../../assets/data_object_relationship_icon_dark.svg",
        import.meta.url,
      ).href
    : new URL(
        "../../../assets/data_object_relationship_icon.svg",
        import.meta.url,
      ).href;
});
const shepardExplainedIconSrc = computed(() => {
  return isDarkMode.value
    ? new URL(
        "../../../assets/shepard_explained_icon_dark.svg",
        import.meta.url,
      ).href
    : new URL("../../../assets/shepard_explained_icon.svg", import.meta.url)
        .href;
});
</script>
<template>
  <div>
    <v-container fluid class="bg-divider2" max-height="270">
      <v-col
        cols="12"
        class="d-flex flex-column align-center justify-center pt-7"
      >
        <v-row>
          <v-img
            src="../../../assets/shepard_logo.svg"
            height="147"
            width="570"
          />
        </v-row>
        <v-row class="text-subtitle-2 mb-7">
          Storage for Heterogeneous Product and Research Data
        </v-row>
      </v-col>
    </v-container>
    <v-container
      fluid
      class="bg-canvas mt-12 d-flex flex-column align-center"
      max-height="570"
    >
      <div class="d-flex justify-center">
        <LandingPageCard
          class="mr-8"
          title="Collections"
          icon-type="collection"
          content-title="Collections"
          content="Organize all the data that belongs to one project in one collection. Within collections you can start providing context for your research data by creating new data objects. Data objects are also were you can log your daily process in the lab journal!"
          button-text="Go to Collections"
          button-link="/collections"
        />
        <LandingPageCard
          title="Containers"
          icon-type="container"
          content-title="Store Raw Data"
          content="Your raw data is stored in containers. There are different containers for different types of data (e.g. timeseries, spatial, etc.). Browse your containers or upload raw data here."
          button-text="Go to Containers"
          button-link="/containers"
        />
      </div>
      <v-img
        class="mt-8 mb-8"
        :src="shepardExplainedIconSrc"
        width="100"
        height="100"
      />
    </v-container>
    <LandingPageSegment
      title="Your data is stored in containers."
      content="Shepard uses various databases to store payloads efficiently. At this point the data does not need to be structured and provides no specific context."
      additional-content="There are databases for timeseries data, spatial data, structured
      data and general files."
      :image-src="containerIconSrc"
      height="270"
      image-width="94"
      image-height="78"
    />
    <LandingPageSegment
      title="The context for your data is provided in collections."
      content="Think of a collection as a folder for your research projects or
            other thematic areas:"
      :image-src="collectionIconSrc"
      height="270"
      image-width="94"
      image-height="94"
      inverted
    />
    <LandingPageSegment
      title="A collection is made up of data objects."
      content="These are the individual parts of your project, like a single experiment, a process step or a part of your overall demonstrator, for example."
      additional-content="Data Objects can have semantic annotations, attributes, and lab journal entries to provide context and metadata."
      :image-src="dataObjectIconSrc"
      image-width="64"
      image-height="64"
    />
    <LandingPageSegment
      title="From your data objects you reference the relevant data in your containers."
      content="Bundle the different sensor data, pictures, etc. in one data object by creating data references. There are different data references for the different types of data."
      additional-content="Data references can have semantic annotations."
      :image-src="dataReferenceIconSrc"
      height="320"
      image-width="315"
      image-height="200"
      inverted
    />
    <LandingPageSegment
      title="The data objects can have relationships to each other."
      content="Use for example the predecessor and successor relationship to model sequential process steps. The parent/child relationship is useful for parts of an assembly that make up the entire demonstrator, for example."
      height="355"
      :image-src="dataObjectRelationshipIconSrc"
      image-width="338"
      image-height="258"
    />
    <v-container
      fluid
      class="d-flex justify-center align-center bg-canvas mt-16 pt-0"
      max-width="815"
    >
      <v-card color="canvas">
        <template #title>
          <div class="text-h3 text-semibold">Shepard Documentation</div>
        </template>
        <template #text>
          <div class="text-body-1">
            Did you know? You can automate your data storage by working with the
            API! Check out the shepard documentation to learn more.
          </div>
        </template>
        <template #actions>
          <div class="d-flex justify-end gap-3 w-100">
            <v-btn
              variant="outlined"
              href="/shepard/api/q/swagger-ui"
              target="_blank"
            >
              Go to API Docs
            </v-btn>
            <v-btn
              class="bg-primary text-canvas"
              variant="flat"
              href="https://gitlab.com/dlr-shepard/shepard/-/wikis/home"
              target="_blank"
            >
              Go to Shepard Documentation
            </v-btn>
          </div>
        </template>
      </v-card>
    </v-container>
  </div>
</template>
