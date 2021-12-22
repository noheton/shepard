<template>
  <span v-if="name">
    <span v-if="name.length < wordCount">
      {{ name }}
    </span>
    <span v-else v-b-tooltip.hover :title="name">
      {{ name.substring(0, wordCount) + "... " }}
    </span>
  </span>
</template>

<script lang="ts">
import Vue from "vue";

export default Vue.extend({
  props: {
    name: {
      type: String,
      required: true,
    },
    wordCount: {
      type: Number,
      default: 50,
    },
  },
});
</script>
