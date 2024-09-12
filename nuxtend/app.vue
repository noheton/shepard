<template>
  <NuxtLayout>
    <v-app>
      <!-- Toolbar -->
      <v-toolbar color="primary" dark>
        <v-toolbar-title>Vuetify App Example</v-toolbar-title>
        <v-btn icon>
          <v-icon>mdi-bell</v-icon>
        </v-btn>
        <v-btn icon>
          <v-icon>mdi-account</v-icon>
        </v-btn>
      </v-toolbar>
      <NuxtPage />
    </v-app>
  </NuxtLayout>
</template>
