<script lang="ts"></script>

<template>
  <div
    class="d-flex w-100 px-8 mt-8"
    style="flex-direction: column; align-items: center"
  >
    <StartHereIcon style="width: 80%; max-width: 320px" class="px-16 mb-8" />

    <p class="mb-4 text-h5">Put your new collection to work!</p>

    <p class="mb-4">
      Add your first data object to start organising your data and
      documenting your progress.
    </p>

    <p>
      New to shepard? Check out the
      <NuxtLink to="/help" style="color: inherit">in-app documentation.</NuxtLink>
    </p>
  </div>
</template>

<style lang="css" scoped>
p {
  text-align: center;
  color: rgb(var(--v-theme-textbody2));
}
</style>
