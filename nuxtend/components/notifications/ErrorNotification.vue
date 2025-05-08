<script setup lang="ts">
const errorAlert = defineModel({ type: Boolean, default: false });

const error = ref<
  { situation: string; exception?: string; message: string } | undefined
>(undefined);

onError(e => {
  if (isString(e.error)) {
    error.value = {
      message: e.error,
      situation: e.situation,
    };
  } else {
    error.value = {
      exception: e.error.exception,
      message: e.error.message,
      situation: e.situation,
    };
  }
  errorAlert.value = true;
});
</script>

<template>
  <v-snackbar v-model="errorAlert" timeout="-1">
    <div class="text-body-1">
      Error while {{ error?.situation }}: {{ error?.exception }}
    </div>
    <div class="text-body-2">{{ error?.message }}</div>
    <template #actions>
      <v-btn
        variant="text"
        @click="
          errorAlert = false;
          error = undefined;
        "
      >
        Close
      </v-btn>
    </template>
  </v-snackbar>
</template>

<style lang="scss" scoped>
.v-snackbar {
  :deep(.v-overlay__content) {
    background-color: rgb(var(--v-theme-error));
    color: rgb(var(--v-theme-white));
    word-wrap: anywhere;
  }
}
</style>
