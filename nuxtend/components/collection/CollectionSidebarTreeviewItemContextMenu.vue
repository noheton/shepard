<template>
  <v-menu class="pa-0">
    <template #activator="{ props }">
      <v-btn
        icon="mdi-dots-horizontal"
        v-bind="props"
        variant="plain"
        density="compact"
        color="primary"
      />
    </template>
    <v-list>
      <v-list-item color="canvas" density="compact">
        <slot />
      </v-list-item>
    </v-list>
  </v-menu>
</template>

<style lang="scss" scoped>
.v-list {
  padding-top: 0;
  padding-bottom: 0;

  :deep(.v-list-item) {
    padding: 0;
  }
}
</style>
