<script setup lang="ts">
import { useTimeoutFn } from "@vueuse/core";

export interface AutoCompleteItem {
  title?: string;
  value?: object;
}

const { startSearch } = defineProps<{
  isDisabled?: boolean;
  isLoading?: boolean;
  itemList: AutoCompleteItem[];
  startSearch: () => void;
}>();

const emit = defineEmits<{
  (e: "searchEnded", value: object | null): void;
}>();

const autoCompleteModel = defineModel<AutoCompleteItem>();
const searchString = defineModel<string | undefined>("searchString", {
  required: true,
});
const searchDone = defineModel<boolean>("searchDone", {
  required: true,
});

const { isPending, start } = useTimeoutFn(() => {
  if (!searchString.value) {
    searchDone.value = false;
  }
  startSearch();
}, 350);

const onSearch = async (search: string) => {
  searchString.value = search;
  if (isPending.value === false) {
    start();
  }
};

const onSelection = (selectedItem: AutoCompleteItem | null) => {
  if (selectedItem && selectedItem.value) {
    autoCompleteModel.value = selectedItem;
    emit("searchEnded", autoCompleteModel.value);
  } else {
    searchString.value = undefined;
    autoCompleteModel.value = undefined;
    emit("searchEnded", null);
  }
};
</script>

<template>
  <v-autocomplete
    label="Owner"
    :model-value="autoCompleteModel"
    :items="itemList"
    :loading="isLoading"
    :hide-no-data="!searchDone"
    :disabled="isDisabled"
    return-object
    hide-details
    density="comfortable"
    variant="outlined"
    no-data-text="No Search Results"
    color="primary"
    @update:search="onSearch"
    @update:model-value="onSelection"
  />
</template>
