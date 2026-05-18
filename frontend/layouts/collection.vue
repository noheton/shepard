<script setup lang="ts">
import { useDisplay } from "vuetify";
const { mobile } = useDisplay();
const sidebarOpen = ref(false);
</script>

<template>
  <DefaultLayout>
    <!-- Mobile: sidebar in a temporary navigation drawer -->
    <v-navigation-drawer
      v-if="mobile"
      v-model="sidebarOpen"
      location="left"
      temporary
      width="320"
    >
      <CollectionSidebar />
    </v-navigation-drawer>

    <v-container fluid class="pa-0 fill-height align-start overflow-x-auto">
      <v-row no-gutters class="fill-height">
        <!-- Desktop: sidebar as a fixed column -->
        <v-col v-if="!mobile" cols="3">
          <CollectionSidebar />
        </v-col>
        <v-col :cols="mobile ? 12 : 9" :class="mobile ? 'pa-3' : 'pa-8'">
          <!-- Mobile: button to open the sidebar drawer -->
          <div v-if="mobile" class="mb-3">
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
