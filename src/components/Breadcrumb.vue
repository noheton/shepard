<script setup lang="ts">
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

const parentId = ref<string | undefined>();

watch(dataObjectId, newDataObjectId => {
  if (newDataObjectId) retrieveParentId();
  else parentId.value = undefined;
});

function retrieveParentId() {
  DataObjectService.getDataObject({
    collectionId: +collectionId.value,
    dataObjectId: +dataObjectId.value,
  })
    .then(response => {
      if (response.parentId) parentId.value = String(response.parentId);
      else parentId.value = undefined;
    })
    .catch(e => {
      parentId.value = undefined;
      logError(e as ResponseError, "fetching data object for breadcrumbs");
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
    if (parentId.value) {
      ret.push(
        getDataObjectBreadcrumb(collectionId.value, parentId.value, true),
      );
    }
    ret.push(
      getDataObjectBreadcrumb(collectionId.value, dataObjectId.value, false),
    );
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
    text: "Collection",
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
  isParent: boolean,
): Breadcrumb {
  return {
    text: isParent ? "Parent" : "DataObject",
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
  margin-top: 30px;
  margin-bottom: 0px;
  padding: 0px;
  background-color: var(--white);
}

hr {
  margin-top: 4px;
  border-top: 1px solid var(--info);
}
</style>
