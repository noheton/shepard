<script setup lang="ts">
import type { User } from "@dlr-shepard/backend-client";
import { useCollectionPermissionUserSearch } from "~/components/common/permissions/useCollectionPermissionUserSearch";
import AutocompleteInput, {
  type AutoCompleteItem,
} from "../AutocompleteInput.vue";

defineProps<{ isOwner?: boolean }>();

const emit = defineEmits<{
  (e: "ownerChange", value: User): void;
}>();

const owner = defineModel<User>({ required: true });

const searchString = ref<string | undefined>(undefined);

const searchDone = ref<boolean>(false);

const { ownerSearchResults, isLoading, startSearch } =
  useCollectionPermissionUserSearch(searchString, () => {
    searchDone.value = true;
  });

const mapToAutocompleteItem = (user: User): AutoCompleteItem => ({
  title: `${user.firstName} ${user.lastName}`,
  value: user,
});

const ownerModel = computed(() => mapToAutocompleteItem(owner.value));

const onSelect = (selectedItem: AutoCompleteItem | null) => {
  if (selectedItem?.value) {
    owner.value = selectedItem.value as User;
    emit("ownerChange", owner.value);
  }
};
</script>

<template>
  <AutocompleteInput
    v-model:search-string="searchString"
    v-model:search-done="searchDone"
    label="Owner"
    density="compact"
    :model-value="ownerModel"
    :is-disabled="!isOwner"
    :is-loading="isLoading"
    :item-list="ownerSearchResults.map(mapToAutocompleteItem)"
    :start-search="startSearch"
    @search-ended="onSelect"
  />
</template>
