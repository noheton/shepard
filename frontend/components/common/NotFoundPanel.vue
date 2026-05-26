<script setup lang="ts">
const router = useRouter();
</script>

<template>
  <v-empty-state
    icon="mdi-alert-circle-outline"
    title="Not found"
    text="The resource you are looking for does not exist or has been deleted."
  >
    <template #actions>
      <v-btn variant="tonal" @click="router.back()">Go back</v-btn>
    </template>
  </v-empty-state>
</template>
