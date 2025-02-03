<script setup lang="ts">
import {
  getAttributesArrayOfObject,
  getObjectOfAttributesArray,
} from "./attributesObjectUtil";

const props = defineProps<{ attributes: { [key: string]: string } }>();
const emit = defineEmits<{
  (e: "attributesChanged", value: { [key: string]: string }): void;
}>();

const attributesArray = ref<{ key: string; value: string }[]>(
  getAttributesArrayOfObject(props.attributes),
);

function updateAttribute(
  index: number,
  attribute: { key: string; value: string },
) {
  attributesArray.value[index] = attribute;
}

watch(
  attributesArray,
  arr => emit("attributesChanged", getObjectOfAttributesArray(arr)),
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
  <template v-for="({ key, value }, index) in attributesArray" :key="index">
    <v-row align="center">
      <v-col cols="12" class="pb-1 d-flex">
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
          class="text-textbody1 text-body-1"
          icon="mdi-delete-outline"
          size="compact"
          variant="text"
          :style="{ width: '40px' }"
          @click="attributesArray.splice(index, 1)"
        />
      </v-col>
    </v-row>
  </template>
  <v-row>
    <v-col class="pt-4">
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
