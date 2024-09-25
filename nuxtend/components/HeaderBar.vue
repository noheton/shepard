<template>
  <v-app-bar class="border-b-sm">
    <v-app-bar-title>
      <v-btn to="/">
        <v-img src="../assets/shepard_logo.svg" height="29" width="153" />
      </v-btn>
      <v-btn
        to="/collections"
        :style="{
          fontSize: '16px',
          fontWeight: 400,
          fontStyle: 'normal',
          lineHeight: '26px',
          textTransform: 'none',
        }"
      >
        Collections
      </v-btn>
    </v-app-bar-title>
    <template v-slot:append>
      <v-btn icon="mdi-theme-light-dark" @click="toggleTheme" />
      <v-btn prepend-icon="mdi-account" @click="handleAuth()">
        {{ isSignedIn ? "Sign Out" : "Sign In" }}
      </v-btn>
    </template>
  </v-app-bar>
</template>

<script setup>
import { useTheme } from "vuetify";

const { status, signOut, signIn, token } = useAuth();
const theme = useTheme();

const isSignedIn = computed(() => status.value === "authenticated").value;

const handleAuth = () => (isSignedIn ? signOut(token) : signIn());
const toggleTheme = () => {
  theme.global.name.value = theme.global.current.value.dark ? "light" : "dark";
};
</script>
