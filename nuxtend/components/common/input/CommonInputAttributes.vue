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
  arr => {
    console.log("updated", arr);
    emit("attributesChanged", getObjectOfAttributesArray(arr));
  },
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
    <v-row class="pb-3 align-center">
      <v-col class="py-0 pl-0 pr-2">
        <v-text-field
          :model-value="key"
          :rules="attributeRules"
          label="Key"
          variant="outlined"
          density="compact"
          hide-details
          @update:model-value="
            newKey => updateAttribute(index, { key: newKey, value })
          "
        />
      </v-col>
      <v-col class="py-0 px-2">
        <v-text-field
          :model-value="value"
          label="Value"
          variant="outlined"
          density="compact"
          hide-details
          @update:model-value="
            newValue => updateAttribute(index, { key, value: newValue })
          "
        />
      </v-col>
      <v-col cols="auto" class="py-0 pr-0 pl-2">
        <v-btn
          class="text-textbody1 text-body-1"
          icon="mdi-delete-outline"
          variant="text"
          size="compact"
          @click="attributesArray.splice(index, 1)"
        />
      </v-col>
    </v-row>
  </template>
  <v-row class="pt-1">
    <v-btn
      text="Add Attributes"
      color="treeview"
      prepend-icon="mdi-plus-circle"
      @click="attributesArray.push({ key: '', value: '' })"
    />
  </v-row>
</template>
