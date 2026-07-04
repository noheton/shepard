<template>
  <v-container fluid class="pa-0 fill-height align-start overflow-x-auto">
    <v-row no-gutters class="fill-height py-md-16 py-4">
      <v-col class="d-none d-md-flex" cols="1" />
      <v-col cols="12" md="3" class="px-4 px-md-0 mb-4 mb-md-0">
        <slot name="menu" />
      </v-col>
      <!-- LAYOUT-4K-CENTERED-EMPTY-001 / L3: at 4K the previous md="7"
           + 1-col gutters left ~58% of viewport for content; widen to
           md="10" with no right gutter so the content pane reaches the
           edge of the viewport while keeping the left-side menu fixed.
           Inner v-container made fluid so it doesn't re-cap at the xl
           breakpoint (~1185px) inside the wide column. -->
      <v-col cols="12" md="8" class="pl-md-8 px-4 px-md-0">
        <v-container fluid class="elevation-4 px-4 px-md-8 py-6">
          <slot name="content" />
        </v-container>
      </v-col>
    </v-row>
  </v-container>
</template>

<style lang="scss" scoped>
.v-container {
  background-color: rgb(var(--v-theme-treeview));

  .v-container {
    background-color: rgb(var(--v-theme-canvas));
    height: 100%;
    border-radius: 4px;
  }
}
</style>
