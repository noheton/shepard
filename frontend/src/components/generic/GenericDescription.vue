<script setup lang="ts">
import TextEditor from "@/components/generic/TextEditor.vue";
import { computed, ref } from "vue";

const props = defineProps({
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
const currentText = computed(() =>
  readMore.value || props.text.length <= props.wordCount
    ? props.text
    : props.text.substring(0, props.wordCount) + "...",
);
</script>

<template>
  <div v-if="currentText">
    <h4>Description</h4>
    <div>
      <TextEditor v-model="currentText" read-only></TextEditor>
      <b-link
        v-if="text.length > wordCount"
        class="float-right"
        @click="readMore = !readMore"
      >
        <span v-if="readMore">read less</span>
        <span v-else>read more</span>
      </b-link>
    </div>
  </div>
</template>
