<script setup lang="ts">
import { useContextualHelp } from "~/composables/useContextualHelp";

const { helpPage } = useContextualHelp();
</script>

<template>
  <v-btn
    v-if="helpPage"
    icon="mdi-help-circle-outline"
    variant="text"
    aria-label="Help for this page"
    data-testid="contextual-help-btn"
    :to="{ path: '/help', query: { page: helpPage } }"
  >
    <v-icon>mdi-help-circle-outline</v-icon>
    <v-tooltip activator="parent" location="bottom">Help for this page</v-tooltip>
  </v-btn>
</template>
