<template>
  <v-tooltip
    location="end"
    :content-props="{
      class: 'text-body-3 ',
    }"
  >
    <template #activator="{ props }">
      <v-icon icon="mdi-information-outline" v-bind="props" />
    </template>
    <slot />
  </v-tooltip>
</template>
