<script setup lang="ts">
const inputString = defineModel<string>("inputString", {
  required: true,
});

const props = defineProps<{
  label: string;
  // optional?: { type: boolean; default: false };
}>();

const attrs = useAttrs();
const isOptional: boolean = attrs["optional"] !== undefined;

const validationRules = [
  (value: unknown) => {
    if (value || isOptional) return true;
    return `${props.label} is required.`;
  },
];
</script>

<template>
  <v-text-field
    v-model:model-value="inputString"
    :rules="validationRules"
    :label="`${label}${isOptional ? '' : '*'}`"
    variant="outlined"
    density="compact"
    :require="!isOptional"
    color="primary"
    hide-details
  />
</template>
