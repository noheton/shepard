<script setup lang="ts">
import type { DataObject } from "@dlr-shepard/backend-client";

interface DataObjectLabJournalProps {
  dataObject: DataObject;
}
defineProps<DataObjectLabJournalProps>();
</script>

<template>
  <v-container fluid>
    <v-row>Ich bin das lab journal</v-row>
  </v-container>
</template>
