<script lang="ts" setup>
import type { User } from "@dlr-shepard/backend-client";
import { usePermissionUserSearch } from "~/composables/common/permissions/usePermissionUserSearch";
import AutocompleteInput, {
  type AutoCompleteItem,
} from "../AutocompleteInput.vue";

defineProps<{ isOwner?: boolean }>();

const emit = defineEmits<{
  (e: "ownerChange", value: User | null): void;
}>();

const owner = defineModel<User>({ required: true });

const searchString = ref<string | undefined>(undefined);

const searchDone = ref<boolean>(false);

const { ownerSearchResults, isLoading, startSearch } = usePermissionUserSearch(
  searchString,
  () => {
    searchDone.value = true;
  },
);

const mapToAutocompleteItem = (user: User): AutoCompleteItem => ({
  title: `${user.firstName} ${user.lastName}`,
  value: user,
});

const ownerModel = computed(() => mapToAutocompleteItem(owner.value));

const onSelect = (selectedItem: AutoCompleteItem | null) => {
  if (selectedItem?.value) {
    owner.value = selectedItem.value as User;
    emit("ownerChange", owner.value);
  } else {
    emit("ownerChange", null);
  }
};
</script>

<template>
  <AutocompleteInput
    v-model:search-done="searchDone"
    v-model:search-string="searchString"
    :is-disabled="!isOwner"
    :is-loading="isLoading"
    :item-list="ownerSearchResults.map(mapToAutocompleteItem)"
    :model-value="ownerModel"
    :start-search="startSearch"
    density="compact"
    label="Owner"
    @search-ended="onSelect"
  />
</template>
