<template>
  <v-app-bar>
    <v-app-bar-title>
      <v-btn to="/">
        <v-img src="../assets/shepard_logo.svg" height="25" width="125" />
      </v-btn>
      <v-btn to="/collections">Collections</v-btn>
    </v-app-bar-title>
    <v-btn prepend-icon="mdi-account" @click="handleAuth()">
      {{ isSignedIn ? "Sign Out" : "Sign In" }}
    </v-btn>
  </v-app-bar>
</template>

<script setup>
const { status, signOut, signIn, token } = useAuth();
const isSignedIn = computed(() => status.value === "authenticated").value;
const handleAuth = () => (isSignedIn ? signOut(token) : signIn());
</script>
