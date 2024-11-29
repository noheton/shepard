<script setup lang="ts">
const viewAll = defineModel<boolean>("viewAll", {
  required: true,
});
</script>

<template>
  <v-btn
    class="opacity-100 pa-0"
    :prepend-icon="viewAll ? 'mdi-chevron-up' : 'mdi-chevron-down'"
    variant="plain"
    base-color="blue-500"
    :ripple="false"
    @click="viewAll = !viewAll"
  >
    {{ viewAll ? "Collapse" : "View All" }}
  </v-btn>
</template>
