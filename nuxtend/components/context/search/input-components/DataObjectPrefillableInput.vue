<script lang="ts" setup>
import type { AutoCompleteItem } from "~/components/common/AutocompleteInput.vue";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import { DataObjectApi } from "@dlr-shepard/backend-client";

const props = defineProps<{
  collectionId: number;
  isRequired?: boolean;
}>();

const dataObjectId = defineModel<number | undefined>("dataObjectId", {
  required: true,
});

const searchString = ref<string | undefined>();
const searchDone = ref<boolean>(false);

const selectedItem = ref<AutoCompleteItem | undefined>(undefined);

const { dataObjectSearchResults, isLoading, startSearch } = useDataObjectSearch(
  props.collectionId,
  searchString,
  () => {
    searchDone.value = true;
  },
);

function reset() {
  dataObjectId.value = undefined;
  selectedItem.value = undefined;
  dataObjectSearchResults.value = [];
  searchString.value = undefined;
  searchDone.value = false;
}

watch(dataObjectId, () => {
  if (dataObjectId.value === undefined) reset();
});

watch(selectedItem, () => {
  if (selectedItem.value) {
    const res = selectedItem.value?.value as DataObjectSearchResult;
    dataObjectId.value = res.dataObjectId;
  }
});

if (dataObjectId.value) {
  isLoading.value = true;
  try {
    const dataObject = await useShepardApi(DataObjectApi).value.getDataObject({
      collectionId: props.collectionId,
      dataObjectId: dataObjectId.value,
    });
    selectedItem.value = mapToSearchResultAutoCompleteItem({
      dataObjectId: dataObject.id,
      dataObjectName: dataObject.name,
    });
  } catch (error) {
    handleError(error, "fetching data object from url parameters");
  }
  isLoading.value = false;
}

function mapToSearchResultAutoCompleteItem(
  searchResult: DataObjectSearchResult,
): AutoCompleteItem {
  return {
    title: `${searchResult.dataObjectName} (ID: ${searchResult.dataObjectId})`,
    value: searchResult,
  };
}

const itemList = computed(() =>
  dataObjectSearchResults.value.map(mapToSearchResultAutoCompleteItem),
);

watch(searchString, (newValue, _) => {
  if (!newValue) reset();
});
</script>

<template>
  <AutocompleteInput
    v-model="selectedItem"
    v-model:search-done="searchDone"
    v-model:search-string="searchString"
    :is-loading="isLoading"
    :item-list="itemList"
    :label="`Data Object Name or ID...${props.isRequired ? `*` : ``}`"
    :start-search="() => startSearch(collectionId)"
    clearable
    density="compact"
    @click:clear="reset"
  />
</template>
