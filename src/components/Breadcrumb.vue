<script setup lang="ts">
import CollectionService from "@/services/collectionService";
import DataObjectService from "@/services/dataObjectService";
import { logError } from "@/utils/error-handling";
import type { ResponseError } from "@dlr-shepard/shepard-client";
import { computed, ref, watch } from "vue";
import type { Location } from "vue-router";
import { useRoute } from "vue2-helpers/vue-router";

interface Breadcrumb {
  text: string;
  to?: Location;
  active: boolean;
}

const route = useRoute();

const collectionId = computed<string>(() => route.params.collectionId);
const dataObjectId = computed<string>(() => route.params.dataObjectId);

const dataObjectName = ref<string>("DataObject");
const collectionName = ref<string>("Collection");

watch(dataObjectId, newDataObjectId => {
  if (newDataObjectId) retrieveDataObject();
});
watch(collectionId, newCollectionId => {
  if (newCollectionId) retrieveCollection();
});

function retrieveDataObject() {
  DataObjectService.getDataObject({
    collectionId: +collectionId.value,
    dataObjectId: +dataObjectId.value,
  })
    .then(response => {
      if (response.name) dataObjectName.value = String(response.name);
    })
    .catch(e => {
      logError(e as ResponseError, "fetching data object for breadcrumbs");
    });
}
function retrieveCollection() {
  CollectionService.getCollection({
    collectionId: +collectionId.value,
  })
    .then(response => {
      if (response.name) collectionName.value = String(response.name);
    })
    .catch(e => {
      logError(e as ResponseError, "fetching collection for breadcrumbs");
    });
}

const items = computed<Breadcrumb[]>(() => chooseBreadcrumb());

function chooseBreadcrumb() {
  const ret: Breadcrumb[] = [];

  if (route.path.startsWith("/explore/")) ret.push(getCollectionsBreadcrumb());

  if (collectionId.value) {
    ret.push(getCollectionBreadcrumb(collectionId.value));
  }

  if (route.path.endsWith("graph")) ret.push({ text: "Graph", active: false });

  if (dataObjectId.value) {
    ret.push(getDataObjectBreadcrumb(collectionId.value, dataObjectId.value));
  }

  // The last element is always active
  ret[ret.length - 1].active = true;
  return ret;
}

function getCollectionsBreadcrumb(): Breadcrumb {
  return {
    text: "Collections",
    active: false,
    to: {
      name: "Explore",
    },
  };
}

function getCollectionBreadcrumb(collectionId: string): Breadcrumb {
  return {
    text: collectionName.value,
    active: false,
    to: {
      name: "Collection",
      params: {
        collectionId: collectionId,
      },
    },
  };
}

function getDataObjectBreadcrumb(
  collectionId: string,
  dataObjectId: string,
): Breadcrumb {
  return {
    text: dataObjectName.value,
    active: false,
    to: {
      name: "DataObject",
      params: {
        collectionId: collectionId,
        dataObjectId: dataObjectId,
      },
    },
  };
}
</script>

<template>
  <div v-if="collectionId">
    <b-breadcrumb class="breadcrumb" :items="items"></b-breadcrumb>
    <hr />
  </div>
</template>

<style scoped>
.breadcrumb {
  font-style: italic;
  margin-top: 20px;
  margin-bottom: 0px;
  padding: 0px;
  background-color: var(--white);
}

hr {
  margin-top: 4px;
  border-top: 1px solid var(--info);
}
</style>
