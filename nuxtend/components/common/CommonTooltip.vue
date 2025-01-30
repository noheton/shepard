<template>
  <div class="d-flex align-baseline">
    <slot />
    <v-tooltip
      location="end"
      :content-props="{
        class: 'text-body-3 tooltip-background',
      }"
    >
      <template #activator="{ props }">
        <v-icon icon="mdi-information-outline" v-bind="props" />
      </template>
      <slot name="content" />
    </v-tooltip>
  </div>
</template>

<style scoped lang="scss">
:deep(.tooltip-background) {
  background: rgb(var(--v-theme-textbody1));
}
</style>
