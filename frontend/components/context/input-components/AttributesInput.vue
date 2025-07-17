<script setup lang="ts">
import {
  getAttributesArrayOfObject,
  getObjectOfAttributesArray,
} from "./attributesObjectUtil";

const attributes = defineModel<{ [key: string]: string }>("attributes", {
  required: true,
});

const attributesArray = ref<{ key: string; value: string }[]>(
  getAttributesArrayOfObject(attributes.value),
);

function updateAttribute(
  index: number,
  attribute: { key: string; value: string },
) {
  attributesArray.value[index] = attribute;
}

watch(
  attributesArray,
  arr => (attributes.value = getObjectOfAttributesArray(arr)),
  { deep: true },
);

const attributeRules = [
  (value: unknown) => {
    if (attributesArray.value.filter(val => val.key === value).length > 1)
      return "Key must be unique";
    if (typeof value === "string" && value.includes("||"))
      return 'Key must not contain "||"';
    return true;
  },
];
</script>

<template>
  <v-row align="center">
    <v-col
      v-for="({ key, value }, index) in attributesArray"
      :key="index"
      cols="12"
      :class="`d-flex ${index !== 0 ? 'py-2' : 'pb-2'}`"
    >
      <v-text-field
        :model-value="key"
        :rules="attributeRules"
        label="Key"
        variant="outlined"
        density="compact"
        color="primary"
        hide-details
        class="pr-2"
        @update:model-value="
          newKey => updateAttribute(index, { key: newKey, value })
        "
      />
      <v-text-field
        :model-value="value"
        label="Value"
        variant="outlined"
        density="compact"
        color="primary"
        hide-details
        class="pr-2"
        @update:model-value="
          newValue => updateAttribute(index, { key, value: newValue })
        "
      />
      <v-btn
        icon="mdi-delete-outline"
        size="compact"
        color="medium-emphasis"
        variant="text"
        :style="{ width: '40px' }"
        @click="attributesArray.splice(index, 1)"
      />
    </v-col>
    <v-col class="pt-3">
      <v-btn
        text="Add Attributes"
        color="treeview"
        variant="flat"
        prepend-icon="mdi-plus-circle"
        @click="attributesArray.push({ key: '', value: '' })"
      />
    </v-col>
  </v-row>
</template>
