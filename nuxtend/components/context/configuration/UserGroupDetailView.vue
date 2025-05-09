<script setup lang="ts">
defineProps<{ userGroupId: number }>();
const emit = defineEmits(["back"]);
</script>

<template>
  <v-btn
    variant="plain"
    color="primary"
    width="fit-content"
    @click="emit('back')"
  >
    Back to User Groups
  </v-btn>
  {{ userGroupId }}
</template>
