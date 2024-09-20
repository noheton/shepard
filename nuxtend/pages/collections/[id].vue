<script setup lang="ts">
const route = useRoute();
const collectionId = parseInt(route.params.id as string);
const selectedDataObjectId = ref<number | undefined>(undefined);
</script>

<template>
  <v-container fluid>
    <v-row>
      <v-col cols="3">
        <CollectionTreeView
          :collection-id="collectionId"
          @data-object-selected="id => (selectedDataObjectId = id)"
        />
      </v-col>
      <v-col cols="9">
        <CollectionDetailView
          :collection-id="collectionId"
          :data-object-id="selectedDataObjectId"
        />
      </v-col>
    </v-row>
  </v-container>
</template>
