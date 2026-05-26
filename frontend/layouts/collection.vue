<script setup lang="ts">
const sidebarOpen = ref(false);
// UX-SIDEBAR-COLLAPSE: desktop collapse toggle.
// When collapsed, the sidebar column shrinks to 48px showing only the toggle
// button; the main content column expands to fill the available space.
const sidebarCollapsed = ref(false);
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
        <!-- Desktop (lg+): sidebar as a fixed column.
             When collapsed, the column is reduced to 48px (just the toggle
             button); when expanded it occupies the normal cols="3" width.
             The d-none + d-lg-flex keeps this out of the mobile DOM entirely. -->
        <v-col
          class="d-none d-lg-flex flex-column"
          :style="sidebarCollapsed ? 'width: 48px; min-width: 48px; max-width: 48px; overflow: hidden' : ''"
          :cols="sidebarCollapsed ? undefined : 3"
        >
          <!-- Collapse/expand toggle button — always visible at desktop -->
          <div class="d-flex justify-end pa-1">
            <v-btn
              :icon="sidebarCollapsed ? 'mdi-menu' : 'mdi-menu-open'"
              size="small"
              variant="text"
              density="compact"
              :title="sidebarCollapsed ? 'Expand sidebar' : 'Collapse sidebar'"
              @click="sidebarCollapsed = !sidebarCollapsed"
            />
          </div>
          <div v-show="!sidebarCollapsed" style="flex: 1; overflow: hidden">
            <CollectionSidebar />
          </div>
        </v-col>
        <!-- Main panel: responsive width + padding via Vuetify breakpoint props.
             Below lg this is the only column (full width, pa-3); at lg+ it
             takes the remaining space beside the sidebar with pa-8.
             When the sidebar is collapsed at lg+, this column naturally expands
             because the sidebar column is only 48px wide. -->
        <v-col cols="12" :lg="sidebarCollapsed ? 12 : 9" class="pa-3 pa-lg-8" style="min-width: 0">
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
