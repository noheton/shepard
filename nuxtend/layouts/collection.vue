<script setup lang="ts">
import DefaultLayout from "~/components/layout-components/DefaultLayout.vue";

const route = useRouter().currentRoute.value;
const collectionId = parseInt(route.params.collectionId as string);
</script>

<template>
  <DefaultLayout>
    <v-container fluid class="pa-0 fill-height align-start">
      <v-row no-gutters class="fill-height">
        <v-col cols="4">
          <CollectionSidebar :collection-id="collectionId" />
        </v-col>
        <v-col cols="8"><slot /></v-col>
      </v-row>
    </v-container>
  </DefaultLayout>
</template>
