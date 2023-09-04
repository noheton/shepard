<script setup lang="ts">
import { ref } from "vue";

defineProps({
  text: {
    type: String,
    required: true,
  },
  wordCount: {
    type: Number,
    default: 300,
  },
});

const readMore = ref<boolean>(false);
</script>

<template>
  <div v-if="text">
    <h4>Description</h4>
    <div class="description">
      <div v-if="text.length < wordCount">
        {{ text }}
      </div>
      <div v-else>
        <span v-if="readMore">
          {{ text }}
        </span>
        <span v-else>
          {{ text.substring(0, wordCount) + "..." }}
        </span>
        <b-link @click="readMore = !readMore">
          <span v-if="readMore">Read Less</span>
          <span v-else>Read More</span>
        </b-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.description {
  font-style: italic;
  text-align: justify;
}
</style>
