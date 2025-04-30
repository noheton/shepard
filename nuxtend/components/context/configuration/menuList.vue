<script setup lang="ts"></script>

<template>
  <div class="d-flex flex-column align-stretch align-self-stretch">
    <span class="text-subtitle-1 pb-2 ml-8">Configuration</span>
    <v-btn
      to="/configuration/semanticrepositories"
      style="justify-content: flex-start"
      class="text-body-1"
      variant="text"
      prepend-icon="mdi-library-outline"
      density="default"
    >
      Semantic Repositories
    </v-btn>
  </div>
</template>

<style lang="scss" scoped>
.v-btn {
  height: 40px;
  padding-left: 2em;
  border-left: 4px solid transparent;
}

.v-btn--active {
  border-left: 4px solid rgb(var(--v-theme-primary));
  border-radius: 0px;

  :deep(.v-btn__overlay) {
    color: rgb(var(--v-theme-primary));
  }
}
</style>
