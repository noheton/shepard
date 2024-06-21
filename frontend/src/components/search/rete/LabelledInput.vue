<!-- eslint-disable vue/no-mutating-props -->
<script setup lang="ts">
import { ref, watch, type PropType } from "vue";

const props = defineProps({
  data: {
    type: Object as PropType<{
      label: string;
      value: string;
      change?: () => void;
    }>,
    required: true,
  },
});

const userInput = ref(props.data.value);

watch(userInput, newUserInput => {
  props.data.value = newUserInput;
  if (props.data.change) props.data.change();
});
</script>

<template>
  <b-form-input
    v-model="userInput"
    class="controlInput"
    :placeholder="data.label"
    size="sm"
    @pointerdown.stop=""
  ></b-form-input>
</template>

<style>
.controlInput {
  border-radius: 10% / 50%;
}
</style>
