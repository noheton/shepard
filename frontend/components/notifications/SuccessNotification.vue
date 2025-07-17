<script setup lang="ts">
const successAlert = defineModel({ type: Boolean, default: false });

const message = ref<string | undefined>(undefined);

onSuccess(e => {
  message.value = e.message;
  successAlert.value = true;
});
</script>

<template>
  <v-snackbar v-model="successAlert" timeout="5000">
    <div class="text-body-1">
      {{ message }}
    </div>
    <template #actions>
      <v-btn color="pink" variant="text" @click="successAlert = false">
        Close
      </v-btn>
    </template>
  </v-snackbar>
</template>

<style lang="scss" scoped>
.v-snackbar {
  :deep(.v-overlay__content) {
    background-color: rgb(var(--v-theme-success));
    color: rgb(var(--v-theme-white));
  }
}
</style>
