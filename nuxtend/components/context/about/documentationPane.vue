<script setup lang="ts">
import { AboutFragments } from "./aboutMenuItems";
</script>

<template>
  <div :id="AboutFragments.DOCUMENTATION" class="d-flex flex-column ga-4">
    <div class="text-h4 pb-4">Documentation</div>
    <div>
      <p>
        <b>For users:</b>
      </p>
      <NuxtLink to="https://gitlab.com/dlr-shepard/shepard/-/wikis/home">
        Link to the official GitLab Wiki
      </NuxtLink>
    </div>
    <div>
      <p>
        <b>For developers and administrators:</b>
      </p>
      <NuxtLink
        to="https://shepard-dlr-shepard-e573f5a4116ef73f64fe76039b5c0aad01da3a88afa.gitlab.io/architecture-docs/"
      >
        Link to the official architecture documentation
      </NuxtLink>
    </div>
  </div>
</template>
