<template>
  <v-container fluid class="pa-0 fill-height align-start overflow-x-auto">
    <v-row no-gutters class="fill-height py-md-16 py-4">
      <v-col class="d-none d-md-flex" cols="1" />
      <v-col cols="12" md="3" class="px-4 px-md-0 mb-4 mb-md-0">
        <slot name="menu" />
      </v-col>
      <v-col cols="12" md="7" class="pl-md-8 px-4 px-md-0">
        <v-container class="elevation-4 px-4 px-md-8 py-6">
          <slot name="content" />
        </v-container>
      </v-col>
      <v-col class="d-none d-md-flex" cols="1" />
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
