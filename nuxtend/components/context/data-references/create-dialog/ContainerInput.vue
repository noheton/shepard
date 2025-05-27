<script setup lang="ts">
import { ContainerType } from "@dlr-shepard/backend-client";
import type { AutoCompleteItem } from "~/components/common/AutocompleteInput.vue";
import { FileContainerAccessor } from "~/composables/container/FileContainerAccessor";
import {
  useContainerSearch,
  type MyContainerSearchResult,
} from "~/composables/context/useContainerSearch";

const props = defineProps<{
  collectionId: number;
  isRequired?: boolean;
  containerType?: ContainerType;
}>();

const containerId = defineModel<number | undefined>("containerId", {
  required: true,
});

const emit = defineEmits<{
  (e: "containerSelected", id: number, type: ContainerType): void;
  (e: "selectionCleared"): void;
}>();

const searchString = ref<string | undefined>(undefined);
const searchDone = ref<boolean>(false);

const autoCompleteModel = ref<AutoCompleteItem | undefined>(undefined);

// Pre-fill input field with already set file container
if (
  containerId.value &&
  props.containerType &&
  props.containerType === ContainerType.File
) {
  const containerAccessor = new FileContainerAccessor(containerId.value);
  await containerAccessor.fetchData();

  if (containerAccessor.fileContainer.value) {
    autoCompleteModel.value = {
      title: `${containerAccessor.fileContainer.value.name} (ID: ${containerAccessor.fileContainer.value.id}) (Type: ${containerAccessor.fileContainer.value.type})`,
      value: {
        containerName: containerAccessor.fileContainer.value.name,
        containerId: containerAccessor.fileContainer.value.id,
        containerType: ContainerType.File,
      },
    };
  }
}

const { containerSearchResults, startSearch, isLoading } = useContainerSearch(
  searchString,
  () => {
    searchDone.value = true;
  },
);

function mapToSearchResultAutoCompleteItem(
  searchResult: MyContainerSearchResult,
): AutoCompleteItem {
  return {
    title: `${searchResult.containerName} (ID: ${searchResult.containerId}) (Type: ${searchResult.containerType})`,
    value: searchResult,
  };
}

const onSelect = (selectedItem: AutoCompleteItem | null) => {
  if (selectedItem?.value) {
    const container = selectedItem.value as MyContainerSearchResult;
    containerId.value = container.containerId;
    emit("containerSelected", container.containerId, container.containerType);
  } else {
    emit("selectionCleared");
  }
};
</script>

<template>
  <v-row>
    <v-col cols="12">
      <AutocompleteInput
        v-model:search-string="searchString"
        v-model:search-done="searchDone"
        :model-value="autoCompleteModel"
        :label="`Container Name or ID...${isRequired ? `*` : ``}`"
        density="compact"
        :is-loading="isLoading"
        :item-list="
          containerSearchResults.map(mapToSearchResultAutoCompleteItem)
        "
        clearable
        :start-search="startSearch"
        @search-ended="onSelect"
      />
    </v-col>
  </v-row>
</template>
