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
        <a @click="readMore = !readMore">
          <span v-if="readMore" class="moreorless">Read Less</span>
          <span v-else class="moreorless">Read More</span>
        </a>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import Vue from "vue";

interface DescriptionData {
  readMore: boolean;
}

export default Vue.extend({
  props: {
    text: {
      type: String,
      required: true,
    },
    wordCount: {
      type: Number,
      default: 300,
    },
  },
  data() {
    return {
      readMore: false,
    } as DescriptionData;
  },
});
</script>

<style scoped>
.description {
  font-style: italic;
  text-align: justify;
}
.moreorless {
  font-style: italic;
  color: blue;
}
</style>
