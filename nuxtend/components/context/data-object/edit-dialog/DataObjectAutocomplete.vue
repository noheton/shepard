<script setup lang="ts">
import { DataObjectApi, type DataObject } from "@dlr-shepard/backend-client";
import { useTimeoutFn } from "@vueuse/core";

interface AutoCompleteItem {
  title?: string;
  value?: DataObjectSearchResult;
}

interface DataObjectAutocompleteProps {
  collectionId: number;
  initialDataObjectId?: number | null;
  inputLabel: string;
  isDisabled?: boolean;
}

const props = defineProps<DataObjectAutocompleteProps>();
const emit = defineEmits<{
  (e: "searchEnded", value: { id: number; name: string } | null): void;
}>();
defineExpose({
  clearInput,
});

const autoCompleteModel = ref<AutoCompleteItem | undefined>(undefined);
const searchString = ref<string | undefined>(undefined);
const hideNoDataMessage = ref<boolean>(true);

const { dataObjectSearchResults, startSearch, isLoading } = useDataObjectSearch(
  props.collectionId,
  searchString,
  () => {
    hideNoDataMessage.value = false;
  },
);

const { isPending, start } = useTimeoutFn(() => {
  if (!searchString.value) {
    hideNoDataMessage.value = true;
  }
  startSearch(props.collectionId);
}, 350);

const onSelection = (selectedItem: AutoCompleteItem | null) => {
  if (selectedItem && selectedItem.value) {
    autoCompleteModel.value = selectedItem;
    emit("searchEnded", {
      id: selectedItem.value.dataObjectId,
      name: selectedItem.value.dataObjectName,
    });
  } else {
    searchString.value = undefined;
    autoCompleteModel.value = undefined;
    emit("searchEnded", null);
  }
};

const onSearch = async (search: string) => {
  searchString.value = search;
  if (isPending.value === false) {
    start();
  }
};

onMounted(async () => {
  if (props.initialDataObjectId && props.initialDataObjectId != -1) {
    const initialDataObject = await getDataObjectById(
      props.initialDataObjectId,
    );
    autoCompleteModel.value = {
      title: `${initialDataObject.name} (ID: ${initialDataObject.id})`,
    };
  }
});

async function getDataObjectById(dataObjectId: number): Promise<DataObject> {
  return await createApiInstance(DataObjectApi).getDataObject({
    collectionId: props.collectionId,
    dataObjectId,
  });
}

function mapToSearchResultAutoCompleteItem(
  searchResult: DataObjectSearchResult,
): AutoCompleteItem {
  return {
    title: `${searchResult.dataObjectName} (ID: ${searchResult.dataObjectId})`,
    value: searchResult,
  };
}

function clearInput() {
  autoCompleteModel.value = undefined;
}
</script>

<template>
  <v-autocomplete
    :model-value="autoCompleteModel"
    :items="dataObjectSearchResults.map(mapToSearchResultAutoCompleteItem)"
    :loading="isLoading"
    :hide-no-data="hideNoDataMessage"
    :label="props.inputLabel"
    :disabled="props.isDisabled"
    density="comfortable"
    variant="outlined"
    no-data-text="No Search Results"
    clearable
    color="primary"
    return-object
    hide-details
    @update:model-value="onSelection"
    @update:search="onSearch"
  />
</template>
