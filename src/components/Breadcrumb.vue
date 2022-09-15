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

const routeName = computed<string>(() => route.name || "");
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
      logError(e as ResponseError, "fetching data objects");
    });
}

const items = computed<Breadcrumb[]>(() => chooseBreadcrumb());

function chooseBreadcrumb() {
  const ret: Breadcrumb[] = [];
  if (routeName.value === "Explore") {
    ret.push(getCollectionsBreadcrumb(true));
  } else if (routeName.value === "Collection") {
    ret.push(getCollectionsBreadcrumb(false));
    ret.push(getCollectionBreadcrumb(true, collectionId.value));
  } else if (routeName.value === "DataObject") {
    ret.push(getCollectionsBreadcrumb(false));
    ret.push(getCollectionBreadcrumb(false, collectionId.value));
    if (parentId.value) {
      ret.push(
        getDataObjectBreadcrumb(
          false,
          collectionId.value,
          parentId.value,
          true,
        ),
      );
    }
    ret.push(
      getDataObjectBreadcrumb(
        true,
        collectionId.value,
        dataObjectId.value,
        false,
      ),
    );
  }
  return ret;
}

function getCollectionsBreadcrumb(active: boolean): Breadcrumb {
  return {
    text: "Collections",
    active: active,
    to: {
      name: "Explore",
    },
  };
}

function getCollectionBreadcrumb(
  active: boolean,
  collectionId: string,
): Breadcrumb {
  return {
    text: "Collection",
    active: active,
    to: {
      name: "Collection",
      params: {
        collectionId: collectionId,
      },
    },
  };
}

function getDataObjectBreadcrumb(
  active: boolean,
  collectionId: string,
  dataObjectId: string,
  isParent: boolean,
): Breadcrumb {
  return {
    text: isParent ? "Parent" : "DataObject",
    active: active,
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
  background-color: #fff;
}

hr {
  margin-top: 4px;
  border-top: 1px solid #343a40;
}
</style>
