<!-- eslint-disable vue/no-mutating-props -->
<script setup lang="ts">
import { ref, watch, type PropType } from "vue";

const props = defineProps({
  data: {
    type: Object as PropType<{
      label: string;
      options: Map<string, string>;
      value: string;
      change?: () => void;
    }>,
    required: true,
  },
});

const options: { value: string; text: string; disabled?: boolean }[] = [
  { value: "", text: props.data.label, disabled: true },
];
props.data.options.forEach((value, key) => {
  options.push({ value: key, text: value });
});

const userInput = ref(props.data.value);

watch(userInput, newUserInput => {
  props.data.value = newUserInput;
  if (props.data.change) props.data.change();
});
</script>

<template>
  <div @pointerdown.stop="">
    <b-form-select
      v-model="userInput"
      class="controlInput"
      :options="options"
      size="sm"
    ></b-form-select>
  </div>
</template>

<style>
.controlInput {
  border-radius: 10% / 50%;
}
</style>
