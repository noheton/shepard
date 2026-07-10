<script setup lang="ts">
import { useTimeoutFn } from "@vueuse/core";
export interface AutoCompleteItem {
  title?: string;
  value?: object;
}

defineSlots();
const { startSearch, density = "default" } = defineProps<{
  isDisabled?: boolean;
  isLoading?: boolean;
  density?: "default" | "comfortable" | "compact";
  label: string;
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

watch(searchString, (newVal, oldVal) => {
  if (newVal === "" && autoCompleteModel.value) {
    emit("searchEnded", autoCompleteModel.value);
  } else if (newVal !== oldVal) {
    if (newVal !== autoCompleteModel.value?.title) emit("searchEnded", null);
  }
});

watch(autoCompleteModel, newVal => {
  if (!newVal) {
    emit("searchEnded", null);
  }
});
</script>

<template>
  <!--
    UIRULE-DROPDOWN-SEARCH-SORT: this shared wrapper is a *server-side-search*
    autocomplete (`startSearch` + `custom-filter=() => true` delegates filtering
    to the backend). `itemList` is caller-supplied, relevance-ranked search
    results — deliberately NOT natural-sorted here, which would clobber the
    server ranking. Callers over a *client-held* list sort their own :items.
  -->
  <v-autocomplete
    ref="autocompleteRef"
    :label="label"
    :model-value="autoCompleteModel"
    :items="itemList"
    :loading="isLoading"
    :hide-no-data="!searchDone"
    :disabled="isDisabled"
    return-object
    hide-details
    :density="density"
    variant="outlined"
    no-data-text="No Search Results"
    color="primary"
    :custom-filter="() => true"
    @update:search="onSearch"
    @update:model-value="onSelection"
  >
    <template v-for="(_, slot) of $slots" #[slot]="scope">
      <slot :name="slot" v-bind="scope" />
    </template>
  </v-autocomplete>
</template>
