<script setup lang="ts">
useHead({
  title: "shepard",
});

const { status } = useAuth();

// Show the personal digest when authenticated; fall back to the public
// landing page while auth is resolving or when unauthenticated.
const isAuthenticated = computed(() => status.value === "authenticated");
</script>

<template>
  <PersonalDigest v-if="isAuthenticated" />
  <LandingPage v-else />
</template>
