<script setup lang="ts">
interface CollectionDetailViewProps {
  collectionId: number;
  dataObjectId: number | undefined;
}

const props = defineProps<CollectionDetailViewProps>();
</script>

<template>
  <v-container fluid>
    <v-row>
      <v-col cols="12">
        Details for DataObject {{ props.dataObjectId }} in collection
        {{ props.collectionId }}
      </v-col>
    </v-row>
  </v-container>
</template>
