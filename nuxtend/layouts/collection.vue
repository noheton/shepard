<script setup lang="ts">
import DefaultLayout from "~/components/layout-components/DefaultLayout.vue";

const route = useRouter().currentRoute.value;
const collectionId = parseInt(route.params.collectionId as string);

let dataObjectId: number | undefined = undefined;
if (
  route.params.dataObjectId &&
  typeof route.params.dataObjectId === "string"
) {
  dataObjectId = parseInt(route.params.dataObjectId as string);
}
</script>

<template>
  <DefaultLayout>
    <v-container fluid class="pa-0 fill-height align-start">
      <v-row no-gutters class="fill-height">
        <v-col cols="3">
          <CollectionSidebar
            :collection-id="collectionId"
            :data-object-id="dataObjectId"
          />
        </v-col>
        <v-col cols="9" class="pa-8"><slot /></v-col>
      </v-row>
    </v-container>
  </DefaultLayout>
</template>
