<template>
  <v-container fluid class="pa-0 fill-height align-start overflow-x-auto">
    <v-row no-gutters class="fill-height py-16">
      <v-col cols="1" />
      <v-col cols="3">
        <slot name="menu" />
      </v-col>
      <v-col cols="7" class="pl-8">
        <v-container class="elevation-4 px-8 py-6">
          <slot name="content" />
        </v-container>
      </v-col>
      <v-col cols="1" />
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
