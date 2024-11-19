<script setup lang="ts">
const route = useRoute();
const collectionId = parseInt(route.params.id as string);
const selectedDataObjectId = ref<number | undefined>(undefined);
</script>

<template>
  <v-container fluid class="pa-0 fill-height align-start">
    <v-row no-gutters class="fill-height">
      <v-col cols="4">
        <CollectionTreeView
          :collection-id="collectionId"
          @data-object-selected="id => (selectedDataObjectId = id)"
        />
      </v-col>
      <v-col cols="8">
        <CollectionDetailView
          :collection-id="collectionId"
          :data-object-id="selectedDataObjectId"
        />
      </v-col>
    </v-row>
  </v-container>
</template>
