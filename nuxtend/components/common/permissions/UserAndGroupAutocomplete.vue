<script setup lang="ts">
import {
  instanceOfUser,
  type User,
  type UserGroup,
} from "@dlr-shepard/backend-client";
import type { AutoCompleteItem } from "../AutocompleteInput.vue";
import {
  useUserAndGroupSearch,
  type UserOrGroup,
} from "./useUserAndGroupSearch";

const emit = defineEmits<{
  (e: "userSelect", value: User): void;
  (e: "userGroupSelect", value: UserGroup): void;
}>();

const searchString = ref<string | undefined>(undefined);
const searchDone = ref<boolean>(false);

const { searchResults, isLoading, startSearch } = useUserAndGroupSearch(
  searchString,
  () => {
    searchDone.value = true;
  },
);

const mapToAutocompleteItem = (userOrGroup: UserOrGroup): AutoCompleteItem => {
  if (instanceOfUser(userOrGroup))
    return {
      title: `${userOrGroup.firstName} ${userOrGroup.lastName}`,
      value: userOrGroup as User,
    };
  else
    return {
      title: `${userOrGroup.name} (UsergroupID: ${userOrGroup.id})`,
      value: userOrGroup as UserGroup,
    };
};

const onSelect = (selectedItem: AutoCompleteItem | null) => {
  if (selectedItem?.value) {
    if (instanceOfUser(selectedItem.value))
      emit("userSelect", selectedItem.value);
    else emit("userGroupSelect", selectedItem as UserGroup);
  }
};
</script>
<template>
  <AutocompleteInput
    v-model:search-string="searchString"
    v-model:search-done="searchDone"
    label="User or group id"
    density="compact"
    :is-loading="isLoading"
    :item-list="searchResults.map(mapToAutocompleteItem)"
    :start-search="startSearch"
    @search-ended="onSelect"
  />
</template>
