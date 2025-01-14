<script setup lang="ts">
import { getCollectionRouterParamsFromRoute } from "~/components/collection/collectionUtils";
import DefaultLayout from "~/components/layout-components/DefaultLayout.vue";

const route = useRouter().currentRoute.value;

const routeParams = getCollectionRouterParamsFromRoute(route.params)!;
</script>

<template>
  <DefaultLayout>
    <v-container fluid class="pa-0 fill-height align-start">
      <v-row no-gutters class="fill-height">
        <v-col cols="3">
          <CollectionSidebar :collection-route-params="routeParams" />
        </v-col>
        <v-col cols="9" class="pa-8 overflow-x-auto"><slot /></v-col>
      </v-row>
    </v-container>
  </DefaultLayout>
</template>
