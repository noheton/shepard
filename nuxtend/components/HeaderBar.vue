<template>
  <v-toolbar>
    <v-toolbar-title>
      <v-btn to="/">
        <v-img src="../assets/Shepard_Logo.svg" height="25" width="125" />
      </v-btn>
      <v-btn to="/collections">Collections</v-btn>
    </v-toolbar-title>
    <v-btn prepend-icon="mdi-account" @click="handleAuth()">
      {{ isSignedIn ? "Sign Out" : "Sign In" }}
    </v-btn>
  </v-toolbar>
</template>

<script setup>
const { status, signOut, signIn, token } = useAuth();
const isSignedIn = computed(() => status.value === "authenticated").value;
const handleAuth = () => (isSignedIn ? signOut(token) : signIn());
</script>
