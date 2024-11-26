<script setup lang="ts">
import type { DataObject } from "@dlr-shepard/backend-client";

interface DataObjectAttributesProps {
  dataObject: DataObject;
}
defineProps<DataObjectAttributesProps>();
</script>

<template>
  <v-container fluid>
    <v-row
      v-for="(attributeKey, index) in Object.keys(dataObject.attributes ?? {})"
      :key="index"
    >
      {{ attributeKey }}: {{ dataObject.attributes?.[attributeKey] }}
    </v-row>
  </v-container>
</template>
