<script setup lang="ts">
const sidebarOpen = ref(false);
</script>

<template>
  <DefaultLayout>
    <!-- Mobile (< lg): sidebar in a temporary navigation drawer.
         Using d-lg-none keeps the drawer in markup on small viewports only,
         driven by CSS media queries (no SSR/hydration race vs. useDisplay). -->
    <v-navigation-drawer
      v-model="sidebarOpen"
      location="left"
      temporary
      width="320"
      class="d-lg-none"
    >
      <CollectionSidebar />
    </v-navigation-drawer>

    <v-container fluid class="pa-0 fill-height align-start overflow-x-auto">
      <v-row no-gutters class="fill-height flex-nowrap">
        <!-- Desktop (lg+): sidebar as a fixed column. CSS-only show/hide via
             d-none + d-lg-block — no JS reactivity, no SSR/hydration mismatch. -->
        <v-col cols="3" class="d-none d-lg-block">
          <CollectionSidebar />
        </v-col>
        <!-- Main panel: responsive width + padding via Vuetify breakpoint props.
             Below lg this is the only column (full width, pa-3); at lg+ it
             takes the remaining 9 cols beside the sidebar with pa-8. -->
        <v-col cols="12" lg="9" class="pa-3 pa-lg-8" style="min-width: 0">
          <!-- Mobile-only button to open the sidebar drawer. -->
          <div class="d-lg-none mb-3">
            <v-btn
              variant="outlined"
              prepend-icon="mdi-menu"
              size="small"
              @click="sidebarOpen = !sidebarOpen"
            >
              Data Objects
            </v-btn>
          </div>
          <slot />
        </v-col>
      </v-row>
    </v-container>
  </DefaultLayout>
</template>
