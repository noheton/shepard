<script setup lang="ts">
import { useTheme } from "vuetify";

const theme = useTheme();

onMounted(() => {
  theme.global.name.value = localStorage.getItem("colorScheme") ?? "light";
});
</script>

<template>
  <NuxtLayout>
    <NuxtPage />
  </NuxtLayout>
  <ErrorNotification />
  <SuccessNotification />
</template>
