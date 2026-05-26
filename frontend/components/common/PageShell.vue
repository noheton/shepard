<template>
  <div class="page-shell">
    <slot />
  </div>
</template>

<style scoped>
.page-shell {
  max-width: min(1600px, 95vw);
  margin: 0 auto;
}
</style>
