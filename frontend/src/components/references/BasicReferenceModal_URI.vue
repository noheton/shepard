<script setup lang="ts">
import type { URIReference } from "@/generated/openapi";
import type { PropType } from "vue";

defineProps({
  uriReference: {
    type: Object as PropType<URIReference>,
    required: true,
  },
});
</script>

<template>
  <div>
    <b-link :href="uriReference.uri" target="_blank">
      {{ uriReference.uri }}
    </b-link>
  </div>
</template>
