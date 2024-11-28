<script setup lang="ts">
import type { DataObject } from "@dlr-shepard/backend-client";

interface DataObjectTitleProps {
  dataObject: DataObject;
}
defineProps<DataObjectTitleProps>();
</script>

<template>
  <v-container fluid class="pt-0">
    <v-row>
      <v-col cols="12">
        <div class="font-weight-medium text-h1">{{ dataObject.name }}</div>
      </v-col>
    </v-row>
    <v-row class="text-body-2 text-black-400">
      <v-col cols="4">Data Object ID: {{ dataObject.id }}</v-col>
      <v-col cols="4">
        Created at: {{ dataObject.createdAt?.toDateString() }} by
        {{ dataObject.createdBy }}
      </v-col>
      <v-col v-if="dataObject.updatedAt" cols="4">
        Updated at: {{ dataObject.updatedAt?.toDateString() }} by
        {{ dataObject.updatedBy }}
      </v-col>
      <v-spacer v-else />
    </v-row>
  </v-container>
</template>
