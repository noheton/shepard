<script setup lang="ts">
const viewAll = defineModel<boolean>("viewAll", {
  required: true,
});
</script>

<template>
  <v-btn
    class="ml-15"
    :prepend-icon="viewAll ? 'mdi-chevron-up' : 'mdi-chevron-down'"
    variant="plain"
    base-color="blue-500"
    :ripple="false"
    @click="viewAll = !viewAll"
  >
    View all
  </v-btn>
</template>

<style scoped>
.v-btn--variant-plain {
  opacity: unset;
}
</style>
